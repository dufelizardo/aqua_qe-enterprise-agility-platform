package br.com.aquaqe.platform.workflow;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.Status;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.StatusTransition;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;
import br.com.aquaqe.platform.workflow.TransitionGuard.FailedRule;

/**
 * Execução de transições (SSD 3.5 e 4.2; SAD 7.3), numa única transação: autorização, versão, transição existente no
 * workflow, guardas e, só então, novo status e histórico.
 */
@Service
class WorkflowService {

	static final int COMMENT_MAX = 1000;

	private final Artifacts artifacts;
	private final ArtifactConfiguration configuration;
	private final List<TransitionGuard> guards;
	private final UserDirectory users;
	private final JdbcClient jdbc;
	private final DomainEvents events;

	WorkflowService(Artifacts artifacts, ArtifactConfiguration configuration, List<TransitionGuard> guards,
			UserDirectory users, JdbcClient jdbc, DomainEvents events) {
		this.artifacts = artifacts;
		this.configuration = configuration;
		this.guards = guards;
		this.users = users;
		this.jdbc = jdbc;
		this.events = events;
	}

	/** Transições possíveis a partir do status atual, e se o usuário pode executá-las. */
	List<TransitionOption> options(String ref) {
		ArtifactRef artifact = artifacts.readable(ref);
		boolean canExecute = !artifact.archived() && artifacts.canEdit(artifact);
		List<TransitionOption> result = new ArrayList<>();
		for (StatusTransition transition : configuration.transitionsFrom(artifact.statusId())) {
			List<FailedRule> failed = evaluate(artifact, transition);
			result.add(new TransitionOption(transition.id(), StatusView.of(transition.to()), canExecute, failed));
		}
		return result;
	}

	@Transactional
	ArtifactView execute(String ref, UUID transitionId, Integer version, String comment) {
		ArtifactRef artifact = artifacts.readable(ref);
		artifacts.requireEdit(artifact);
		if (transitionId == null) {
			throw ApiException.validation("transitionId", "obrigatório");
		}
		if (version == null) {
			throw ApiException.validation("version", "obrigatório");
		}
		if (comment != null && comment.length() > COMMENT_MAX) {
			throw ApiException.validation("comment", "no máximo " + COMMENT_MAX + " caracteres");
		}
		if (artifact.archived()) {
			throw unprocessable("TRANSITION_NOT_ALLOWED", "Item arquivado não muda de status.");
		}
		if (version != artifact.version()) {
			throw ApiException.versionConflict();
		}
		StatusTransition transition = configuration.transitionsFrom(artifact.statusId()).stream()
			.filter(t -> t.id().equals(transitionId))
			.findFirst()
			.orElseThrow(() -> unprocessable("TRANSITION_NOT_ALLOWED", "Este movimento não existe no fluxo."));
		List<FailedRule> failed = evaluate(artifact, transition);
		if (!failed.isEmpty()) {
			// Transação própria: a tentativa vai ser desfeita, mas o bloqueio fica registrado (SSD 4.2).
			events.publishSeparately("artifact.transition_blocked", artifact.actorId(), artifact.orgUnitId(),
				new Aggregate("ARTIFACT", artifact.id(), artifact.key()), Map.of("key", artifact.key(), "transitionId",
					transition.id().toString(), "failedRuleIds", failed.stream().map(FailedRule::ruleId).toList()));
			throw new ApiException(HttpStatus.valueOf(422), "GATE_BLOCKED",
				failed.size() + " regra(s) do gate não foram atendidas.", Map.of("failedRules", failed));
		}
		if (!artifacts.changeStatus(artifact.id(), transition.to().id(), version)) {
			throw ApiException.versionConflict();
		}
		jdbc.sql("""
				insert into workflow.status_history (artifact_id, from_status_id, to_status_id, transition_id, changed_by, comment)
				values (:artifact, :from, :to, :transition, :by, :comment)
				""")
			.param("artifact", artifact.id())
			.param("from", transition.from().id())
			.param("to", transition.to().id())
			.param("transition", transition.id())
			.param("by", artifact.actorId())
			.param("comment", comment == null || comment.isBlank() ? null : comment.strip())
			.update();
		events.publish("artifact.status_changed", artifact.actorId(), artifact.orgUnitId(),
			new Aggregate("ARTIFACT", artifact.id(), artifact.key()),
			Map.of("key", artifact.key(), "from", transition.from().key(), "to", transition.to().key(), "transitionId",
				transition.id().toString()),
			Map.of("status", transition.from().key()), Map.of("status", transition.to().key()));
		return artifacts.view(artifact.id());
	}

	/** Histórico de status do artefato, do mais antigo para o mais recente. */
	List<HistoryItem> history(String ref) {
		ArtifactRef artifact = artifacts.readable(ref);
		List<HistoryRow> rows = jdbc.sql("""
				select from_status_id, to_status_id, changed_by, changed_at, comment from workflow.status_history
				 where artifact_id = :artifact order by changed_at, id
				""")
			.param("artifact", artifact.id())
			.query((rs, n) -> new HistoryRow(rs.getObject("from_status_id", UUID.class),
				rs.getObject("to_status_id", UUID.class), rs.getObject("changed_by", UUID.class),
				rs.getObject("changed_at", OffsetDateTime.class).toInstant(), rs.getString("comment")))
			.list();
		Set<UUID> statusIds = new HashSet<>();
		Set<UUID> userIds = new HashSet<>();
		rows.forEach(r -> {
			statusIds.add(r.from());
			statusIds.add(r.to());
			userIds.add(r.by());
		});
		Map<UUID, Status> statuses = configuration.statuses(statusIds);
		Map<UUID, UserRef> names = users.find(userIds);
		return rows.stream()
			.map(r -> new HistoryItem(statuses.get(r.from()).key(), statuses.get(r.to()).key(), r.at(),
				names.containsKey(r.by()) ? new Person(r.by(), names.get(r.by()).name()) : null, r.comment()))
			.toList();
	}

	private List<FailedRule> evaluate(ArtifactRef artifact, StatusTransition transition) {
		List<FailedRule> failed = new ArrayList<>();
		guards.forEach(guard -> failed.addAll(guard.evaluate(artifact, transition)));
		return failed;
	}

	private static ApiException unprocessable(String code, String detail) {
		return new ApiException(HttpStatus.valueOf(422), code, detail);
	}

	private record HistoryRow(UUID from, UUID to, UUID by, Instant at, String comment) {
	}

	record StatusView(String key, String name, String phase, String category) {

		static StatusView of(Status status) {
			return new StatusView(status.key(), status.name(), status.phase(), status.category());
		}
	}

	/** Uma transição possível; {@code failedRules} lista o que o gate ainda exige (vazia quando liberada). */
	record TransitionOption(UUID id, StatusView to, boolean canExecute, List<FailedRule> failedRules) {
	}

	record Person(UUID id, String name) {
	}

	record HistoryItem(String from, String to, Instant at, Person by, String comment) {
	}
}
