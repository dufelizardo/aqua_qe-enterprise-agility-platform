package br.com.aquaqe.platform.board;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.Status;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.organization.OrgUnits.OrgUnitSummary;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.ArtifactViews.Person;
import br.com.aquaqe.platform.workitem.Artifacts;

/**
 * Quadros (SSD 3.9). Um quadro pertence a uma unidade e a um nível e mostra os artefatos daquele nível na subárvore da
 * unidade, nas colunas que agrupam status do workflow. Mover cartões é executar a transição (SSD 3.5): o quadro não
 * tem endpoint de movimentação.
 */
@Service
class BoardService {

	static final int NAME_MAX = 120;
	/** Limite de cartões por visão, até existirem paginação e filtros por sprint. */
	static final int VIEW_LIMIT = 500;
	private static final Set<String> LEVELS = Set.of("FL1", "FL2", "FL3");

	private final JdbcClient jdbc;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final ArtifactConfiguration configuration;
	private final Artifacts artifacts;

	BoardService(JdbcClient jdbc, OrgUnits orgUnits, Authorization authorization, CurrentUser currentUser,
			ArtifactConfiguration configuration, Artifacts artifacts) {
		this.jdbc = jdbc;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.configuration = configuration;
		this.artifacts = artifacts;
	}

	List<Board> boards(UUID orgUnitId, String level) {
		currentUser.require();
		List<Board> boards = jdbc.sql("""
				select id, org_unit_id, level, name from board.board
				 where (cast(:unit as uuid) is null or org_unit_id = cast(:unit as uuid))
				   and (cast(:level as varchar) is null or level = cast(:level as varchar))
				 order by created_at, id
				""")
			.param("unit", orgUnitId)
			.param("level", level)
			.query((rs, n) -> new Board(rs.getObject("id", UUID.class), rs.getObject("org_unit_id", UUID.class),
				rs.getString("level"), rs.getString("name"), List.of()))
			.list();
		return boards.stream().map(b -> withColumns(b)).toList();
	}

	Board board(UUID id) {
		currentUser.require();
		return find(id).orElseThrow(ApiException::notFound);
	}

	/** Cria o quadro com as colunas padrão: um status por coluna, na ordem do workflow do nível. */
	@Transactional
	Board create(UUID orgUnitId, String level, String name) {
		UUID userId = currentUser.require().id();
		if (orgUnitId == null) {
			throw ApiException.validation("orgUnitId", "obrigatório");
		}
		OrgUnitSummary unit = orgUnits.find(orgUnitId)
			.orElseThrow(() -> ApiException.validation("orgUnitId", "unidade inexistente"));
		if (!unit.active()) {
			throw ApiException.validation("orgUnitId", "unidade desativada");
		}
		if (level == null || !LEVELS.contains(level)) {
			throw ApiException.validation("level", "use FL1, FL2 ou FL3");
		}
		if (!authorization.can(userId, Permission.MANAGE_BOARDS, orgUnitId)) {
			throw ApiException.forbidden();
		}
		String boardName = name == null || name.isBlank() ? unit.name() + " · " + level : name.strip();
		if (boardName.length() > NAME_MAX) {
			throw ApiException.validation("name", "no máximo " + NAME_MAX + " caracteres");
		}
		UUID id = UUID.randomUUID();
		jdbc.sql("insert into board.board (id, org_unit_id, level, name, created_by) values (:id, :unit, :level, :name, :by)")
			.param("id", id).param("unit", orgUnitId).param("level", level).param("name", boardName).param("by", userId)
			.update();
		int position = 1;
		for (Status status : configuration.sequenceOfLevel(level)) {
			jdbc.sql("""
					insert into board.board_column (board_id, position, name, phase, status_keys)
					values (:board, :position, :name, :phase, cast(:keys as text[]))
					""")
				.param("board", id).param("position", position++).param("name", status.name())
				.param("phase", phaseOf(status)).param("keys", new String[] { status.key() })
				.update();
		}
		return find(id).orElseThrow();
	}

	/** Colunas com os cartões que o usuário lê (SSD 2.1, regras 1, 2 e 6). */
	BoardView view(UUID id, String type, UUID assigneeId) {
		Board board = board(id);
		List<ArtifactView> items = artifacts.itemsOfLevel(board.orgUnitId(), board.level(), VIEW_LIMIT);
		Map<String, List<Card>> byStatus = new HashMap<>();
		for (ArtifactView item : items) {
			if ((type != null && !type.equals(item.type()))
					|| (assigneeId != null && (item.assignee() == null || !assigneeId.equals(item.assignee().id())))) {
				continue;
			}
			byStatus.computeIfAbsent(item.status().key(), k -> new ArrayList<>())
				.add(new Card(item.id(), item.key(), item.type(), item.title(), item.owner(), item.size(), null,
					item.version()));
		}
		List<ColumnView> columns = board.columns().stream()
			.map(c -> new ColumnView(c.id(), c.name(), c.phase(), c.statusKeys(),
				c.statusKeys().stream().flatMap(k -> byStatus.getOrDefault(k, List.of()).stream()).toList()))
			.toList();
		return new BoardView(board.id(), board.level(), columns);
	}

	private Optional<Board> find(UUID id) {
		return jdbc.sql("select id, org_unit_id, level, name from board.board where id = :id")
			.param("id", id)
			.query((rs, n) -> new Board(rs.getObject("id", UUID.class), rs.getObject("org_unit_id", UUID.class),
				rs.getString("level"), rs.getString("name"), List.of()))
			.optional()
			.map(this::withColumns);
	}

	private Board withColumns(Board board) {
		List<Column> columns = jdbc.sql("""
				select id, position, name, phase, status_keys from board.board_column
				 where board_id = :board order by position
				""")
			.param("board", board.id())
			.query((rs, n) -> new Column(rs.getObject("id", UUID.class), rs.getInt("position"), rs.getString("name"),
				rs.getString("phase"), keys(rs)))
			.list();
		return new Board(board.id(), board.orgUnitId(), board.level(), board.name(), columns);
	}

	/** Fase da coluna: a do status; nos workflows sem fase (Task, Problema de Produção), A Fazer é Upstream. */
	private static String phaseOf(Status status) {
		if (status.phase() != null) {
			return status.phase();
		}
		return "TODO".equals(status.category()) ? "UPSTREAM" : "DOWNSTREAM";
	}

	private static List<String> keys(ResultSet rs) throws SQLException {
		Array array = rs.getArray("status_keys");
		return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
	}

	record Column(UUID id, int position, String name, String phase, List<String> statusKeys) {
	}

	record Board(UUID id, UUID orgUnitId, String level, String name, List<Column> columns) {
	}

	/** Cartão (UX/UI 6.1). {@code gateSummary} entra com o módulo governance. */
	record Card(UUID id, String key, String type, String title, Person owner, String size, Object gateSummary,
			int version) {
	}

	record ColumnView(UUID id, String name, String phase, List<String> statusKeys, List<Card> cards) {
	}

	record BoardView(UUID boardId, String level, List<ColumnView> columns) {
	}
}
