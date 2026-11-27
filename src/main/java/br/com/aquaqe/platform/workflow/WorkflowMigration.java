package br.com.aquaqe.platform.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.Status;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.WorkflowScope;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.StatusSnapshot;

/**
 * Migração explícita para a versão ativa de um workflow (#49; F05/D-01). Publicar uma versão não move nenhum item; aqui
 * quem gere configuração vê quantos itens estão em outra versão (anteriores, ou o workflow herdado de antes de a unidade
 * ter o próprio) e os leva, status a status: a mesma chave, quando existe na versão ativa, ou o status escolhido.
 * <p>
 * Cada item migrado gera um evento {@code artifact.workflow_migrated} (auditoria). As aprovações dadas no status antigo
 * não acompanham o item. Reexecutar não muda nada: os itens já migrados não aparecem mais.
 */
@Service
class WorkflowMigration {

	private final ArtifactConfiguration configuration;
	private final Artifacts artifacts;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final DomainEvents events;

	WorkflowMigration(ArtifactConfiguration configuration, Artifacts artifacts, OrgUnits orgUnits,
			Authorization authorization, CurrentUser currentUser, DomainEvents events) {
		this.configuration = configuration;
		this.artifacts = artifacts;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.events = events;
	}

	/** Quantos itens estão em outra versão, por status, com o destino sugerido (a mesma chave, se existe). */
	@Transactional(readOnly = true)
	Preview preview(UUID workflowId) {
		WorkflowScope workflow = active(workflowId);
		requireManage(workflow.orgUnitId());
		List<StatusSnapshot> pending = pending(workflow);
		Map<UUID, Status> statuses = configuration.statuses(pending.stream().map(StatusSnapshot::statusId).toList());
		Map<String, Long> counts = pending.stream()
			.collect(Collectors.groupingBy(p -> statuses.get(p.statusId()).key(), LinkedHashMap::new, Collectors.counting()));
		Map<String, Status> targets = byKey(workflow);
		List<PendingStatus> groups = new ArrayList<>();
		counts.forEach((key, count) -> {
			String name = statuses.values().stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow().name();
			groups.add(new PendingStatus(key, name, count, targets.containsKey(key) ? key : null));
		});
		return new Preview(workflow.id(), workflow.versionNumber(), pending.size(), groups,
			workflow.statuses().stream().map(s -> new Target(s.key(), s.name())).toList());
	}

	/** Leva os itens para a versão ativa. {@code mapping} diz o destino de cada status que não existe nela. */
	@Transactional
	Result migrate(UUID workflowId, Map<String, Object> body) {
		WorkflowScope workflow = active(workflowId);
		UUID actor = requireManage(workflow.orgUnitId());
		Map<?, ?> mapping = body != null && body.get("mapping") instanceof Map<?, ?> m ? m : Map.of();
		List<StatusSnapshot> pending = pending(workflow);
		Map<UUID, Status> statuses = configuration.statuses(pending.stream().map(StatusSnapshot::statusId).toList());
		Map<String, Status> targets = byKey(workflow);
		Map<String, Status> destination = new HashMap<>();
		for (Status from : statuses.values()) {
			Object chosen = mapping.get(from.key());
			String targetKey = chosen instanceof String s ? s : targets.containsKey(from.key()) ? from.key() : null;
			if (targetKey == null) {
				throw ApiException.validation("mapping." + from.key(), "escolha o status de destino de " + from.name());
			}
			Status target = targets.get(targetKey);
			if (target == null) {
				throw ApiException.validation("mapping." + from.key(), "status inexistente na versão ativa: " + targetKey);
			}
			destination.put(from.key(), target);
		}
		for (StatusSnapshot item : pending) {
			Status from = statuses.get(item.statusId());
			Status to = destination.get(from.key());
			if (!artifacts.migrateStatus(item.id(), to.id(), item.version())) {
				throw ApiException.versionConflict();
			}
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("key", item.key());
			payload.put("fromStatus", from.key());
			payload.put("toStatus", to.key());
			payload.put("workflowId", workflow.id().toString());
			payload.put("versionNumber", workflow.versionNumber());
			events.publish("artifact.workflow_migrated", actor, item.orgUnitId(),
				new Aggregate("ARTIFACT", item.id(), item.key()), payload, Map.of("status", from.key()),
				Map.of("status", to.key()));
		}
		return new Result(pending.size());
	}

	// ---------------------------------------------------------------------------------------------- apoio

	/**
	 * Itens dos tipos do workflow, na subárvore da unidade dona, para os quais ele é o vigente, e que estão num status que
	 * não é desta versão.
	 */
	private List<StatusSnapshot> pending(WorkflowScope workflow) {
		Set<UUID> units = orgUnits.subtrees(List.of(workflow.orgUnitId()));
		Map<UUID, String> typeKeys = new HashMap<>();
		for (String key : workflow.artifactTypes()) {
			configuration.type(key).map(ArtifactType::id).ifPresent(id -> typeKeys.put(id, key));
		}
		List<UUID> own = workflow.statuses().stream().map(Status::id).toList();
		Map<String, Boolean> effective = new HashMap<>();
		return artifacts.outsideStatuses(units, typeKeys.keySet(), own).stream()
			.filter(item -> effective.computeIfAbsent(item.orgUnitId() + "/" + item.typeId(),
				k -> configuration.effectiveWorkflowId(item.orgUnitId(), typeKeys.get(item.typeId()))
					.map(workflow.id()::equals).orElse(false)))
			.toList();
	}

	private WorkflowScope active(UUID workflowId) {
		WorkflowScope workflow = configuration.workflowScope(workflowId).orElseThrow(ApiException::notFound);
		if (!workflow.active()) {
			throw ApiException.validation("workflow", "migre para a versão ativa do workflow");
		}
		return workflow;
	}

	private static Map<String, Status> byKey(WorkflowScope workflow) {
		Map<String, Status> result = new HashMap<>();
		workflow.statuses().forEach(s -> result.put(s.key(), s));
		return result;
	}

	private UUID requireManage(UUID unitId) {
		UUID user = currentUser.require().id();
		if (!authorization.can(user, Permission.MANAGE_CONFIGURATION, unitId)) {
			throw ApiException.forbidden();
		}
		return user;
	}

	/** Prévia: {@code total} itens em outra versão, agrupados pelo status atual. */
	record Preview(UUID workflowId, int versionNumber, int total, List<PendingStatus> pending, List<Target> targets) {
	}

	/** Itens num status de outra versão; {@code suggestedKey} é a mesma chave, quando existe na versão ativa. */
	record PendingStatus(String statusKey, String statusName, long items, String suggestedKey) {
	}

	record Target(String key, String name) {
	}

	record Result(int migrated) {
	}
}
