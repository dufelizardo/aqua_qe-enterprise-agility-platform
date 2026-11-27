package br.com.aquaqe.platform.board;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * unidade, nas colunas que agrupam status do workflow. As colunas e o limite de WIP são configuráveis (PRD-RF-011;
 * Proposta 04). Mover cartões é executar a transição (SSD 3.5): o quadro não tem endpoint de movimentação.
 */
@Service
class BoardService {

	static final int NAME_MAX = 120;
	static final int COLUMN_NAME_MAX = 80;
	/** Limite de cartões por visão, até existirem paginação e filtros por sprint. */
	static final int VIEW_LIMIT = 500;
	static final String CANCELLED = "CANCELLED";
	private static final Set<String> LEVELS = Set.of("FL1", "FL2", "FL3");
	private static final Set<String> PHASES = Set.of("UPSTREAM", "DOWNSTREAM");
	private static final Set<String> POLICIES = Set.of("WARN", "BLOCK");

	private final JdbcClient jdbc;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final ArtifactConfiguration configuration;
	private final Artifacts artifacts;
	private final SprintService sprints;
	private final BoardTemplates templates;

	BoardService(JdbcClient jdbc, OrgUnits orgUnits, Authorization authorization, CurrentUser currentUser,
			ArtifactConfiguration configuration, Artifacts artifacts, SprintService sprints, BoardTemplates templates) {
		this.jdbc = jdbc;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.configuration = configuration;
		this.artifacts = artifacts;
		this.sprints = sprints;
		this.templates = templates;
	}

	List<Board> boards(UUID orgUnitId, String level) {
		currentUser.require();
		return find(orgUnitId, level);
	}

	/** Quadros, sem exigir usuário (usado também pelo guarda de WIP). */
	List<Board> find(UUID orgUnitId, String level) {
		return jdbc.sql("""
				select id, org_unit_id, level, name from board.board
				 where (cast(:unit as uuid) is null or org_unit_id = cast(:unit as uuid))
				   and (cast(:level as varchar) is null or level = cast(:level as varchar))
				 order by created_at, id
				""")
			.param("unit", orgUnitId)
			.param("level", level)
			.query((rs, n) -> new Board(rs.getObject("id", UUID.class), rs.getObject("org_unit_id", UUID.class),
				rs.getString("level"), rs.getString("name"), List.of(), List.of()))
			.list()
			.stream()
			.map(this::complete)
			.toList();
	}

	Board board(UUID id) {
		currentUser.require();
		return find(id).orElseThrow(ApiException::notFound);
	}

	/** Cria o quadro com as colunas padrão: um status por coluna, na ordem do workflow do nível, sem limite de WIP. */
	@Transactional
	Board create(UUID orgUnitId, String level, String name) {
		return create(orgUnitId, level, name, null);
	}

	/**
	 * Cria o quadro. Com {@code templateId}, as colunas são uma cópia das do template (#50; MDL-07), sem os status que
	 * não valem para a unidade; colunas que ficam sem status são omitidas. Sem template, as colunas padrão.
	 */
	@Transactional
	Board create(UUID orgUnitId, String level, String name, UUID templateId) {
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
		requireManage(userId, orgUnitId);
		String boardName = name == null || name.isBlank() ? unit.name() + " · " + level : boardName(name);
		UUID id = UUID.randomUUID();
		jdbc.sql("insert into board.board (id, org_unit_id, level, name, created_by) values (:id, :unit, :level, :name, :by)")
			.param("id", id).param("unit", orgUnitId).param("level", level).param("name", boardName).param("by", userId)
			.update();
		List<Column> columns = templateId == null ? defaultColumns(level, orgUnitId)
				: templateColumns(templateId, level, orgUnitId);
		insertColumns(id, columns);
		return find(id).orElseThrow();
	}

	private List<Column> defaultColumns(String level, UUID orgUnitId) {
		List<Column> columns = new ArrayList<>();
		for (Status status : configuration.sequenceOfLevel(level, orgUnitId)) {
			columns.add(new Column(null, columns.size() + 1, status.name(), phaseOf(status), List.of(status.key()), null,
				"WARN"));
		}
		return columns;
	}

	private List<Column> templateColumns(UUID templateId, String level, UUID orgUnitId) {
		BoardTemplates.Template template = templates.find(templateId)
			.orElseThrow(() -> ApiException.validation("templateId", "template inexistente"));
		if (!template.level().equals(level)) {
			throw ApiException.validation("templateId", "o template é de " + template.level() + ", e o quadro, de " + level);
		}
		Set<String> allowed = new HashSet<>(levelStatusKeys(level, orgUnitId));
		allowed.add(CANCELLED);
		List<Column> columns = new ArrayList<>();
		for (BoardTemplates.TemplateColumn c : template.columns()) {
			List<String> keys = c.statusKeys().stream().filter(allowed::contains).distinct().toList();
			if (!keys.isEmpty()) {
				columns.add(new Column(null, columns.size() + 1, c.name(), c.phase(), keys, c.wipLimit(),
					c.wipPolicy() == null ? "WARN" : c.wipPolicy()));
			}
		}
		if (columns.isEmpty()) {
			throw ApiException.validation("templateId", "nenhum status do template vale para esta unidade");
		}
		return columns;
	}

	/** Salva as colunas do quadro como template do catálogo (#50). Quem gere o quadro. */
	@Transactional
	BoardTemplates.Template saveAsTemplate(UUID boardId, String name, String description) {
		UUID userId = currentUser.require().id();
		Board board = find(boardId).orElseThrow(ApiException::notFound);
		requireManage(userId, board.orgUnitId());
		return templates.insert(name, board.level(), description, board.columns(), userId, board.orgUnitId(), board.id());
	}

	/**
	 * Altera o nome e, se informadas, substitui as colunas (SSD 3.9, {@code PATCH /boards/{id}}). Colunas com {@code id}
	 * mantêm o identificador; as demais são criadas.
	 */
	@Transactional
	Board update(UUID id, String name, List<ColumnInput> columns) {
		UUID userId = currentUser.require().id();
		Board board = find(id).orElseThrow(ApiException::notFound);
		requireManage(userId, board.orgUnitId());
		if (name != null) {
			jdbc.sql("update board.board set name = :name where id = :id").param("id", id).param("name", boardName(name))
				.update();
		}
		if (columns != null) {
			List<Column> valid = validate(board.level(), board.orgUnitId(), columns);
			jdbc.sql("delete from board.board_column where board_id = :board").param("board", id).update();
			insertColumns(id, valid);
		}
		return find(id).orElseThrow();
	}

	/** Colunas com os cartões que o usuário lê (SSD 2.1, regras 1, 2 e 6) e a situação do WIP de cada uma. */
	BoardView view(UUID id, String type, UUID assigneeId, Boolean blocked, UUID sprintId) {
		Board board = board(id);
		Set<UUID> inSprint = sprintId == null ? null : sprints.itemIdSet(sprintId);
		List<ArtifactView> items = artifacts.itemsOfLevel(board.orgUnitId(), board.level(), VIEW_LIMIT);
		Map<String, List<Card>> byStatus = new HashMap<>();
		for (ArtifactView item : items) {
			if ((type != null && !type.equals(item.type()))
					|| (assigneeId != null && (item.assignee() == null || !assigneeId.equals(item.assignee().id())))
					|| (blocked != null && blocked != item.blocked())
					|| (inSprint != null && !inSprint.contains(item.id()))) {
				continue;
			}
			byStatus.computeIfAbsent(item.status().key(), k -> new ArrayList<>())
				.add(new Card(item.id(), item.key(), item.type(), item.title(), item.owner(), item.size(), null,
					item.version(), item.blocked(), item.blockedReason()));
		}
		List<ColumnView> columns = new ArrayList<>();
		for (Column c : board.columns()) {
			long count = c.wipLimit() == null ? -1 : wipCount(board, c);
			columns.add(new ColumnView(c.id(), c.name(), c.phase(), c.statusKeys(), c.wipLimit(), c.wipPolicy(),
				c.wipLimit() == null ? null : count, wipState(c.wipLimit(), count),
				c.statusKeys().stream().flatMap(k -> byStatus.getOrDefault(k, List.of()).stream()).toList()));
		}
		return new BoardView(board.id(), board.level(), board.unmappedStatusKeys(), columns);
	}

	/** Itens da coluna no quadro, de todos os usuários: o limite vale para a coluna inteira. */
	long wipCount(Board board, Column column) {
		Set<UUID> statusIds = new HashSet<>();
		column.statusKeys().forEach(key -> statusIds.addAll(configuration.statusIds(key, null)));
		return artifacts.countForBoard(board.orgUnitId(), board.level(), statusIds);
	}

	/** {@code NONE} sem limite; {@code UNDER}, {@code AT} (atingido) ou {@code OVER} (ultrapassado). */
	static String wipState(Integer limit, long count) {
		if (limit == null) {
			return "NONE";
		}
		return count < limit ? "UNDER" : count == limit ? "AT" : "OVER";
	}

	// ---------------------------------------------------------------------------------------------- validação

	private List<Column> validate(String level, UUID orgUnitId, List<ColumnInput> inputs) {
		if (inputs.isEmpty()) {
			throw ApiException.validation("columns", "o quadro precisa de ao menos uma coluna");
		}
		Set<String> allowed = new HashSet<>(levelStatusKeys(level, orgUnitId));
		allowed.add(CANCELLED);
		Set<String> used = new HashSet<>();
		boolean downstreamSeen = false;
		List<Column> result = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			ColumnInput in = inputs.get(i);
			String field = "columns[" + i + "]";
			String name = in.name() == null ? "" : in.name().strip();
			if (name.isEmpty() || name.length() > COLUMN_NAME_MAX) {
				throw ApiException.validation(field + ".name", "obrigatório, até " + COLUMN_NAME_MAX + " caracteres");
			}
			if (in.phase() == null || !PHASES.contains(in.phase())) {
				throw ApiException.validation(field + ".phase", "use UPSTREAM ou DOWNSTREAM");
			}
			if ("UPSTREAM".equals(in.phase()) && downstreamSeen) {
				throw ApiException.validation(field + ".phase", "as colunas Upstream vêm antes das Downstream");
			}
			downstreamSeen |= "DOWNSTREAM".equals(in.phase());
			if (in.statusKeys() == null || in.statusKeys().isEmpty()) {
				throw ApiException.validation(field + ".statusKeys", "informe ao menos um status");
			}
			for (String key : in.statusKeys()) {
				if (!allowed.contains(key)) {
					throw ApiException.validation(field + ".statusKeys", "status " + key + " não pertence ao nível " + level);
				}
				if (!used.add(key)) {
					throw ApiException.validation(field + ".statusKeys", "status " + key + " já está em outra coluna");
				}
			}
			if (in.wipLimit() != null && in.wipLimit() < 1) {
				throw ApiException.validation(field + ".wipLimit", "deve ser 1 ou mais; vazio é sem limite");
			}
			String policy = in.wipPolicy() == null ? "WARN" : in.wipPolicy();
			if (!POLICIES.contains(policy)) {
				throw ApiException.validation(field + ".wipPolicy", "use WARN ou BLOCK");
			}
			result.add(new Column(in.id(), i + 1, name, in.phase(), List.copyOf(new LinkedHashSet<>(in.statusKeys())),
				in.wipLimit(), policy));
		}
		return result;
	}

	private List<String> levelStatusKeys(String level, UUID orgUnitId) {
		return configuration.sequenceOfLevel(level, orgUnitId).stream().map(Status::key).toList();
	}

	private void requireManage(UUID userId, UUID orgUnitId) {
		if (!authorization.can(userId, Permission.MANAGE_BOARDS, orgUnitId)) {
			throw ApiException.forbidden();
		}
	}

	private static String boardName(String name) {
		String clean = name.strip();
		if (clean.isEmpty() || clean.length() > NAME_MAX) {
			throw ApiException.validation("name", "obrigatório, até " + NAME_MAX + " caracteres");
		}
		return clean;
	}

	// ---------------------------------------------------------------------------------------------- persistência

	private void insertColumns(UUID boardId, List<Column> columns) {
		for (Column c : columns) {
			jdbc.sql("""
					insert into board.board_column (id, board_id, position, name, phase, status_keys, wip_limit, wip_policy)
					values (coalesce(cast(:id as uuid), gen_random_uuid()), :board, :position, :name, :phase,
					        cast(:keys as text[]), :limit, :policy)
					""")
				.param("id", c.id()).param("board", boardId).param("position", c.position()).param("name", c.name())
				.param("phase", c.phase()).param("keys", c.statusKeys().toArray(String[]::new))
				.param("limit", c.wipLimit()).param("policy", c.wipPolicy())
				.update();
		}
	}

	private Optional<Board> find(UUID id) {
		return jdbc.sql("select id, org_unit_id, level, name from board.board where id = :id")
			.param("id", id)
			.query((rs, n) -> new Board(rs.getObject("id", UUID.class), rs.getObject("org_unit_id", UUID.class),
				rs.getString("level"), rs.getString("name"), List.of(), List.of()))
			.optional()
			.map(this::complete);
	}

	/** Colunas e os status do nível que não estão em nenhuma coluna (aviso da UX/UI 6.2). */
	private Board complete(Board board) {
		List<Column> columns = jdbc.sql("""
				select id, position, name, phase, status_keys, wip_limit, wip_policy from board.board_column
				 where board_id = :board order by position
				""")
			.param("board", board.id())
			.query((rs, n) -> new Column(rs.getObject("id", UUID.class), rs.getInt("position"), rs.getString("name"),
				rs.getString("phase"), keys(rs), rs.getObject("wip_limit", Integer.class), rs.getString("wip_policy")))
			.list();
		Set<String> mapped = new HashSet<>();
		columns.forEach(c -> mapped.addAll(c.statusKeys()));
		List<String> unmapped = levelStatusKeys(board.level(), board.orgUnitId()).stream().filter(k -> !mapped.contains(k)).toList();
		return new Board(board.id(), board.orgUnitId(), board.level(), board.name(), columns, unmapped);
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

	record Column(UUID id, int position, String name, String phase, List<String> statusKeys, Integer wipLimit,
			String wipPolicy) {
	}

	record ColumnInput(UUID id, String name, String phase, List<String> statusKeys, Integer wipLimit, String wipPolicy) {
	}

	/** {@code unmappedStatusKeys}: status do workflow do nível fora de qualquer coluna; os itens neles ficam invisíveis. */
	record Board(UUID id, UUID orgUnitId, String level, String name, List<Column> columns,
			List<String> unmappedStatusKeys) {
	}

	/** Cartão (UX/UI 6.1). {@code gateSummary} entra com o módulo governance. */
	record Card(UUID id, String key, String type, String title, Person owner, String size, Object gateSummary,
			int version, boolean blocked, String blockedReason) {
	}

	/** {@code wipCount} conta todos os itens da coluna, não só os visíveis; vem nulo quando não há limite. */
	record ColumnView(UUID id, String name, String phase, List<String> statusKeys, Integer wipLimit, String wipPolicy,
			Long wipCount, String wipState, List<Card> cards) {
	}

	record BoardView(UUID boardId, String level, List<String> unmappedStatusKeys, List<ColumnView> columns) {
	}
}
