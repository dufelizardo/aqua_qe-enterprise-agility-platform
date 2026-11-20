package br.com.aquaqe.platform.board;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Sprints opcionais por squad (SSD 3.9; PRD-RF-015). Estados {@code PLANNED} → {@code ACTIVE} → {@code CLOSED}; no
 * máximo uma ativa por squad; só itens de FL1 da própria squad; cada item em no máximo uma sprint não encerrada; ao
 * encerrar, os itens não concluídos saem da sprint e voltam ao backlog. Squads em fluxo contínuo não criam sprints.
 */
@Service
class SprintService {

	static final int NAME_MAX = 120;
	static final int GOAL_MAX = 1000;

	private final JdbcClient jdbc;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final Artifacts artifacts;
	private final ArtifactConfiguration configuration;

	SprintService(JdbcClient jdbc, OrgUnits orgUnits, Authorization authorization, CurrentUser currentUser,
			Artifacts artifacts, ArtifactConfiguration configuration) {
		this.jdbc = jdbc;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.artifacts = artifacts;
		this.configuration = configuration;
	}

	/** Sprints da squad: a ativa, as planejadas e as encerradas, nessa ordem. */
	List<Sprint> sprints(UUID squadId) {
		currentUser.require();
		squad(squadId);
		return jdbc.sql(SELECT + """
				 where s.squad_id = :squad
				 order by case s.state when 'ACTIVE' then 1 when 'PLANNED' then 2 else 3 end,
				          s.start_date nulls last, s.created_at
				""")
			.param("squad", squadId)
			.query((rs, n) -> sprint(rs))
			.list();
	}

	@Transactional
	Sprint create(UUID squadId, String name, String goal, String startDate, String endDate) {
		if (squadId == null) {
			throw ApiException.validation("squadId", "obrigatório");
		}
		OrgUnitSummary squad = orgUnits.find(squadId)
			.orElseThrow(() -> ApiException.validation("squadId", "unidade inexistente"));
		if (!"FL1".equals(squad.flightLevel()) || !squad.active()) {
			throw ApiException.validation("squadId", "use uma unidade ativa de FL1");
		}
		requireManage(squadId);
		LocalDate start = date("startDate", startDate);
		LocalDate end = date("endDate", endDate);
		checkDates(start, end);
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				insert into board.sprint (id, squad_id, name, goal, start_date, end_date)
				values (:id, :squad, :name, :goal, :start, :end)
				""")
			.param("id", id).param("squad", squadId).param("name", requireName(name)).param("goal", goal(goal))
			.param("start", start).param("end", end)
			.update();
		return find(id).orElseThrow();
	}

	/** Altera nome, objetivo e datas (merge patch); sprint encerrada não muda. */
	@Transactional
	Sprint update(UUID id, Map<String, Object> body) {
		Sprint sprint = find(id).orElseThrow(ApiException::notFound);
		requireManage(sprint.squadId());
		requireNotClosed(sprint);
		String name = body.containsKey("name") ? requireName(text(body, "name")) : sprint.name();
		String goal = body.containsKey("goal") ? goal(text(body, "goal")) : sprint.goal();
		LocalDate start = body.containsKey("startDate") ? date("startDate", text(body, "startDate")) : sprint.startDate();
		LocalDate end = body.containsKey("endDate") ? date("endDate", text(body, "endDate")) : sprint.endDate();
		checkDates(start, end);
		jdbc.sql("update board.sprint set name = :name, goal = :goal, start_date = :start, end_date = :end where id = :id")
			.param("id", id).param("name", name).param("goal", goal).param("start", start).param("end", end)
			.update();
		return find(id).orElseThrow();
	}

	@Transactional
	Sprint start(UUID id) {
		Sprint sprint = find(id).orElseThrow(ApiException::notFound);
		requireManage(sprint.squadId());
		if (!"PLANNED".equals(sprint.state())) {
			throw conflict("SPRINT_STATE", "Só uma sprint planejada pode ser iniciada.");
		}
		boolean otherActive = jdbc.sql("select exists (select 1 from board.sprint where squad_id = :squad and state = 'ACTIVE')")
			.param("squad", sprint.squadId()).query(Boolean.class).single();
		if (otherActive) {
			throw conflict("SPRINT_ALREADY_ACTIVE", "A squad já tem uma sprint ativa. Encerre-a antes.");
		}
		jdbc.sql("update board.sprint set state = 'ACTIVE', start_date = coalesce(start_date, current_date) where id = :id")
			.param("id", id).update();
		return find(id).orElseThrow();
	}

	/** Encerra: itens não concluídos saem da sprint e voltam ao backlog; os concluídos ficam no histórico dela. */
	@Transactional
	CloseResult close(UUID id) {
		Sprint sprint = find(id).orElseThrow(ApiException::notFound);
		requireManage(sprint.squadId());
		if (!"ACTIVE".equals(sprint.state())) {
			throw conflict("SPRINT_STATE", "Só a sprint ativa pode ser encerrada.");
		}
		List<UUID> notDone = new ArrayList<>();
		Map<UUID, UUID> statusOf = itemStatuses(id);
		Map<UUID, Status> statuses = configuration.statuses(Set.copyOf(statusOf.values()));
		statusOf.forEach((artifact, status) -> {
			if (!"DONE".equals(statuses.get(status).category())) {
				notDone.add(artifact);
			}
		});
		for (UUID artifact : notDone) {
			jdbc.sql("delete from board.sprint_item where sprint_id = :sprint and artifact_id = :artifact")
				.param("sprint", id).param("artifact", artifact).update();
		}
		jdbc.sql("update board.sprint set state = 'CLOSED', end_date = coalesce(end_date, current_date) where id = :id")
			.param("id", id).update();
		return new CloseResult(find(id).orElseThrow(), notDone.size());
	}

	/** Itens da sprint que o usuário lê, na ordem do backlog. */
	List<ArtifactView> items(UUID id) {
		currentUser.require();
		find(id).orElseThrow(ApiException::notFound);
		List<ArtifactView> result = new ArrayList<>();
		for (UUID artifact : itemIds(id)) {
			try {
				result.add(artifacts.view(artifacts.readable(artifact.toString()).id()));
			} catch (ApiException notReadable) {
				// Fora do escopo de leitura do usuário (SSD 2.1, regra 6): não aparece.
			}
		}
		return result;
	}

	@Transactional
	void addItem(UUID id, String artifactRef) {
		Sprint sprint = find(id).orElseThrow(ApiException::notFound);
		requireManage(sprint.squadId());
		requireNotClosed(sprint);
		ArtifactRef artifact = artifacts.readable(artifactRef);
		if (!"FL1".equals(artifact.level()) || !sprint.squadId().equals(artifact.orgUnitId())) {
			throw unprocessable("SPRINT_ITEM_NOT_ALLOWED", "Só itens de FL1 da própria squad entram na sprint.");
		}
		if (artifact.archived()) {
			throw unprocessable("SPRINT_ITEM_NOT_ALLOWED", "Item arquivado não entra na sprint.");
		}
		Optional<String> other = jdbc.sql("""
				select s.name from board.sprint_item i join board.sprint s on s.id = i.sprint_id
				 where i.artifact_id = :artifact and s.state <> 'CLOSED' and s.id <> :sprint
				""")
			.param("artifact", artifact.id()).param("sprint", id).query(String.class).optional();
		if (other.isPresent()) {
			throw conflict("ITEM_IN_ANOTHER_SPRINT", "O item já está na sprint \"" + other.get() + "\".");
		}
		jdbc.sql("insert into board.sprint_item (sprint_id, artifact_id) values (:sprint, :artifact) on conflict do nothing")
			.param("sprint", id).param("artifact", artifact.id()).update();
	}

	@Transactional
	void removeItem(UUID id, String artifactRef) {
		Sprint sprint = find(id).orElseThrow(ApiException::notFound);
		requireManage(sprint.squadId());
		requireNotClosed(sprint);
		ArtifactRef artifact = artifacts.readable(artifactRef);
		jdbc.sql("delete from board.sprint_item where sprint_id = :sprint and artifact_id = :artifact")
			.param("sprint", id).param("artifact", artifact.id()).update();
	}

	/** Ids dos itens de uma sprint (para o filtro {@code sprintId} da visão do quadro). */
	Set<UUID> itemIdSet(UUID id) {
		find(id).orElseThrow(() -> ApiException.validation("sprintId", "sprint inexistente"));
		return Set.copyOf(itemIds(id));
	}

	// ---------------------------------------------------------------------------------------------- apoio

	private static final String SELECT = """
		select s.id, s.squad_id, s.name, s.goal, s.start_date, s.end_date, s.state,
		       (select count(*) from board.sprint_item i where i.sprint_id = s.id) as item_count
		  from board.sprint s
		""";

	private Optional<Sprint> find(UUID id) {
		return jdbc.sql(SELECT + " where s.id = :id").param("id", id).query((rs, n) -> sprint(rs)).optional();
	}

	private List<UUID> itemIds(UUID sprintId) {
		return jdbc.sql("select artifact_id from board.sprint_item where sprint_id = :sprint")
			.param("sprint", sprintId).query(UUID.class).list();
	}

	private Map<UUID, UUID> itemStatuses(UUID sprintId) {
		Map<UUID, UUID> result = new java.util.HashMap<>();
		for (UUID artifact : itemIds(sprintId)) {
			result.put(artifact, artifacts.statusOf(artifact));
		}
		return result;
	}

	private void squad(UUID squadId) {
		orgUnits.find(squadId).orElseThrow(ApiException::notFound);
	}

	private void requireManage(UUID squadId) {
		UUID userId = currentUser.require().id();
		if (!authorization.can(userId, Permission.MANAGE_BOARDS, squadId)) {
			throw ApiException.forbidden();
		}
	}

	private static void requireNotClosed(Sprint sprint) {
		if ("CLOSED".equals(sprint.state())) {
			throw conflict("SPRINT_STATE", "Sprint encerrada não muda.");
		}
	}

	private static String requireName(String name) {
		String clean = name == null ? "" : name.strip();
		if (clean.isEmpty() || clean.length() > NAME_MAX) {
			throw ApiException.validation("name", "obrigatório, até " + NAME_MAX + " caracteres");
		}
		return clean;
	}

	private static String goal(String goal) {
		if (goal == null || goal.isBlank()) {
			return null;
		}
		if (goal.strip().length() > GOAL_MAX) {
			throw ApiException.validation("goal", "no máximo " + GOAL_MAX + " caracteres");
		}
		return goal.strip();
	}

	private static LocalDate date(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException e) {
			throw ApiException.validation(field, "use o formato AAAA-MM-DD");
		}
	}

	private static void checkDates(LocalDate start, LocalDate end) {
		if (start != null && end != null && end.isBefore(start)) {
			throw ApiException.validation("endDate", "não pode ser antes do início");
		}
	}

	private static String text(Map<String, Object> body, String field) {
		Object value = body.get(field);
		if (value != null && !(value instanceof String)) {
			throw ApiException.validation(field, "deve ser texto");
		}
		return (String) value;
	}

	private static ApiException conflict(String code, String detail) {
		return new ApiException(HttpStatus.CONFLICT, code, detail);
	}

	private static ApiException unprocessable(String code, String detail) {
		return new ApiException(HttpStatus.valueOf(422), code, detail);
	}

	private static Sprint sprint(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new Sprint(rs.getObject("id", UUID.class), rs.getObject("squad_id", UUID.class), rs.getString("name"),
			rs.getString("goal"), rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
			rs.getString("state"), rs.getLong("item_count"));
	}

	record Sprint(UUID id, UUID squadId, String name, String goal, LocalDate startDate, LocalDate endDate, String state,
			long itemCount) {
	}

	/** {@code returnedToBacklog}: itens não concluídos que saíram da sprint ao encerrar. */
	record CloseResult(Sprint sprint, int returnedToBacklog) {
	}
}
