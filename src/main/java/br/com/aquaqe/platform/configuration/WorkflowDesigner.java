package br.com.aquaqe.platform.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.Status;
import br.com.aquaqe.platform.configuration.ConfigurationRepository.OrgUnitRef;
import br.com.aquaqe.platform.configuration.ConfigurationRepository.Transition;
import br.com.aquaqe.platform.configuration.ConfigurationRepository.Workflow;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.organization.OrgUnits.OrgUnitSummary;
import br.com.aquaqe.platform.shared.ApiException;

/**
 * Workflow Designer (#49; UI-26; SSD 3.3): workflow próprio por unidade, com versões imutáveis.
 * <ul>
 * <li>O workflow vigente de um tipo numa unidade é o ativo da unidade mais funda da linhagem que tenha um; a raiz sempre
 * tem (os padrões da seção 8.2).</li>
 * <li>"Personalizar" copia o workflow herdado para a unidade (versão 1 de uma família nova), com os mesmos tipos.</li>
 * <li>Publicar cria uma versão nova da família e desativa a anterior. Os artefatos ficam na versão em que estão
 * (F05/D-01): nenhuma migração é silenciosa; migrar é explícito ({@code POST /workflows/{id}/migration}).</li>
 * <li>Os gates vão junto, transição a transição, pela chave dos status ({@link WorkflowVersionPublished}).</li>
 * </ul>
 */
@Service
class WorkflowDesigner {

	static final Set<String> PHASES = Set.of("UPSTREAM", "DOWNSTREAM");
	static final Set<String> CATEGORIES = Set.of("TODO", "IN_PROGRESS", "DONE");
	static final Pattern KEY = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
	static final int MAX_STATUSES = 30;
	static final int NAME_MAX = 80;
	static final int WORKFLOW_NAME_MAX = 120;

	private final ConfigurationRepository repository;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final DomainEvents events;
	private final ApplicationEventPublisher publisher;

	WorkflowDesigner(ConfigurationRepository repository, OrgUnits orgUnits, Authorization authorization,
			CurrentUser currentUser, DomainEvents events, ApplicationEventPublisher publisher) {
		this.repository = repository;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.events = events;
		this.publisher = publisher;
	}

	// ---------------------------------------------------------------------------------------------- leitura

	/** Os workflows vigentes na unidade, um por família, com a unidade de onde cada um vem. */
	@Transactional(readOnly = true)
	List<UnitWorkflow> ofUnit(UUID unitId) {
		currentUser.require();
		unit(unitId);
		Map<UUID, UnitWorkflow> result = new LinkedHashMap<>();
		for (ArtifactType type : repository.types()) {
			repository.effectiveWorkflowId(unitId, type.key()).ifPresent(id -> result.computeIfAbsent(id, k -> {
				Workflow workflow = repository.workflow(k).orElseThrow();
				boolean inherited = workflow.orgUnit() == null || !unitId.equals(workflow.orgUnit().id());
				return new UnitWorkflow(workflow, inherited, workflow.orgUnit());
			}));
		}
		return result.values().stream()
			.sorted((a, b) -> a.workflow().name().compareToIgnoreCase(b.workflow().name()))
			.toList();
	}

	// ---------------------------------------------------------------------------------------------- escrita

	/** Copia o workflow herdado para a unidade: a unidade passa a ter o próprio, para os mesmos tipos. */
	@Transactional
	Workflow customize(UUID unitId, Map<String, Object> body) {
		UUID user = requireManage(unitId);
		OrgUnitSummary unit = unit(unitId);
		if (!unit.active()) {
			throw ApiException.validation("unit", "unidade desativada");
		}
		UUID fromId = uuid(body == null ? null : body.get("from"), "from");
		Workflow from = repository.workflow(fromId)
			.orElseThrow(() -> ApiException.validation("from", "workflow inexistente"));
		if (!from.active()) {
			throw ApiException.validation("from", "use a versão ativa do workflow");
		}
		if (from.orgUnit() != null && unitId.equals(from.orgUnit().id())) {
			throw ApiException.validation("from", "a unidade já tem este workflow como próprio");
		}
		for (String type : from.artifactTypes()) {
			if (!repository.effectiveWorkflowId(unitId, type).map(fromId::equals).orElse(false)) {
				throw ApiException.validation("from", "o workflow não é o vigente na unidade para " + type);
			}
		}
		UUID id = UUID.randomUUID();
		repository.insertWorkflow(id, id, from.name(), 1, unitId, user);
		repository.insertTypes(id, from.artifactTypes());
		Map<UUID, UUID> transitions = copy(id, from.statuses().stream().map(StatusInput::of).toList(),
			from.transitions().stream().map(t -> new TransitionInput(t.from(), t.to())).toList(), from);
		publisher.publishEvent(new WorkflowVersionPublished(from.id(), id, transitions, user));
		events.publish("workflow.customized", user, unitId, new Aggregate("WORKFLOW", id, from.name()),
			Map.of("workflowId", id.toString(), "copiedFrom", from.id().toString(), "artifactTypes", from.artifactTypes()));
		return repository.workflow(id).orElseThrow();
	}

	/**
	 * Publica uma nova versão: status e transições informados por inteiro. A versão anterior fica inativa, e os artefatos
	 * nela continuam nela até a migração explícita.
	 */
	@Transactional
	Workflow publish(UUID workflowId, Map<String, Object> body) {
		Workflow current = repository.workflow(workflowId).orElseThrow(ApiException::notFound);
		UUID unitId = current.orgUnit().id();
		UUID user = requireManage(unitId);
		if (!current.active()) {
			throw ApiException.versionConflict();
		}
		String name = workflowName(body == null ? null : body.get("name"), current.name());
		List<StatusInput> statuses = statuses(body == null ? null : body.get("statuses"));
		List<TransitionInput> transitions = transitions(body.get("transitions"), statuses);
		if (!repository.deactivate(current.id())) {
			throw ApiException.versionConflict();
		}
		UUID id = UUID.randomUUID();
		int version = current.versionNumber() + 1;
		repository.insertWorkflow(id, current.familyId(), name, version, unitId, user);
		repository.insertTypes(id, current.artifactTypes());
		Map<UUID, UUID> copied = copy(id, statuses, transitions, current);
		publisher.publishEvent(new WorkflowVersionPublished(current.id(), id, copied, user));
		Set<String> before = new HashSet<>(current.statuses().stream().map(Status::key).toList());
		Set<String> after = new HashSet<>(statuses.stream().map(StatusInput::key).toList());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("workflowId", id.toString());
		payload.put("previousWorkflowId", current.id().toString());
		payload.put("versionNumber", version);
		payload.put("addedStatuses", after.stream().filter(k -> !before.contains(k)).sorted().toList());
		payload.put("removedStatuses", before.stream().filter(k -> !after.contains(k)).sorted().toList());
		events.publish("workflow.version_published", user, unitId, new Aggregate("WORKFLOW", id, name), payload);
		return repository.workflow(id).orElseThrow();
	}

	/** A unidade deixa de ter workflow próprio e volta a herdar o de cima. Os artefatos ficam na versão em que estão. */
	@Transactional
	void revert(UUID workflowId) {
		Workflow current = repository.workflow(workflowId).orElseThrow(ApiException::notFound);
		UUID unitId = current.orgUnit().id();
		UUID user = requireManage(unitId);
		if (unitId.equals(orgUnits.rootId())) {
			throw ApiException.validation("workflow", "os workflows da raiz não podem ser removidos");
		}
		if (!current.active() || !repository.deactivate(current.id())) {
			throw ApiException.versionConflict();
		}
		events.publish("workflow.removed", user, unitId, new Aggregate("WORKFLOW", current.id(), current.name()),
			Map.of("workflowId", current.id().toString(), "artifactTypes", current.artifactTypes()));
	}

	// ---------------------------------------------------------------------------------------------- apoio

	/** Grava status e transições da nova versão; devolve transição de origem → transição nova, pela chave dos status. */
	private Map<UUID, UUID> copy(UUID workflowId, List<StatusInput> statuses, List<TransitionInput> transitions,
			Workflow from) {
		Map<String, UUID> statusIds = new HashMap<>();
		int position = 0;
		for (StatusInput s : statuses) {
			Integer pos = s.inSequence() ? ++position : null;
			statusIds.put(s.key(), repository.insertStatus(workflowId, s.key(), s.name(), s.phase(), s.category(), pos));
		}
		Map<String, UUID> previous = new HashMap<>();
		for (Transition t : from.transitions()) {
			previous.put(t.from() + ">" + t.to(), t.id());
		}
		Map<UUID, UUID> mapping = new HashMap<>();
		for (TransitionInput t : transitions) {
			UUID id = repository.insertTransition(workflowId, statusIds.get(t.from()), statusIds.get(t.to()));
			UUID old = previous.get(t.from() + ">" + t.to());
			if (old != null) {
				mapping.put(old, id);
			}
		}
		return mapping;
	}

	private static List<StatusInput> statuses(Object value) {
		if (!(value instanceof List<?> list) || list.isEmpty()) {
			throw ApiException.validation("statuses", "informe ao menos um status");
		}
		if (list.size() > MAX_STATUSES) {
			throw ApiException.validation("statuses", "até " + MAX_STATUSES + " status");
		}
		List<StatusInput> result = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			String field = "statuses[" + i + "]";
			if (!(list.get(i) instanceof Map<?, ?> m)) {
				throw ApiException.validation(field, "status inválido");
			}
			String key = m.get("key") instanceof String k ? k.strip() : "";
			if (!KEY.matcher(key).matches()) {
				throw ApiException.validation(field + ".key", "use letras maiúsculas, números e _ (até 40), começando por letra");
			}
			if (!keys.add(key)) {
				throw ApiException.validation(field + ".key", "chave repetida: " + key);
			}
			String name = m.get("name") instanceof String n ? n.strip() : "";
			if (name.isEmpty() || name.length() > NAME_MAX) {
				throw ApiException.validation(field + ".name", "obrigatório, até " + NAME_MAX + " caracteres");
			}
			Object phase = m.get("phase");
			if (phase != null && !(phase instanceof String p && PHASES.contains(p))) {
				throw ApiException.validation(field + ".phase", "use UPSTREAM, DOWNSTREAM ou vazio");
			}
			if (!(m.get("category") instanceof String category) || !CATEGORIES.contains(category)) {
				throw ApiException.validation(field + ".category", "use TODO, IN_PROGRESS ou DONE");
			}
			boolean inSequence = !(m.get("inSequence") instanceof Boolean b) || b;
			result.add(new StatusInput(key, name, (String) phase, category, inSequence));
		}
		if (result.stream().noneMatch(StatusInput::inSequence)) {
			throw ApiException.validation("statuses", "ao menos um status precisa estar na sequência");
		}
		return result;
	}

	private static List<TransitionInput> transitions(Object value, List<StatusInput> statuses) {
		if (value != null && !(value instanceof List<?>)) {
			throw ApiException.validation("transitions", "lista de transições");
		}
		Set<String> keys = new HashSet<>(statuses.stream().map(StatusInput::key).toList());
		Set<String> seen = new HashSet<>();
		List<TransitionInput> result = new ArrayList<>();
		List<?> list = value == null ? List.of() : (List<?>) value;
		for (int i = 0; i < list.size(); i++) {
			String field = "transitions[" + i + "]";
			if (!(list.get(i) instanceof Map<?, ?> m) || !(m.get("from") instanceof String from)
					|| !(m.get("to") instanceof String to)) {
				throw ApiException.validation(field, "informe from e to");
			}
			if (!keys.contains(from) || !keys.contains(to)) {
				throw ApiException.validation(field, "status inexistente na versão");
			}
			if (from.equals(to)) {
				throw ApiException.validation(field, "a transição liga dois status diferentes");
			}
			if (!seen.add(from + ">" + to)) {
				throw ApiException.validation(field, "transição repetida");
			}
			result.add(new TransitionInput(from, to));
		}
		return result;
	}

	private static String workflowName(Object value, String current) {
		if (value == null) {
			return current;
		}
		String name = value instanceof String s ? s.strip() : "";
		if (name.isEmpty() || name.length() > WORKFLOW_NAME_MAX) {
			throw ApiException.validation("name", "obrigatório, até " + WORKFLOW_NAME_MAX + " caracteres");
		}
		return name;
	}

	private static UUID uuid(Object value, String field) {
		try {
			return UUID.fromString((String) value);
		} catch (RuntimeException e) {
			throw ApiException.validation(field, "obrigatório");
		}
	}

	private OrgUnitSummary unit(UUID id) {
		return orgUnits.find(id).orElseThrow(ApiException::notFound);
	}

	private UUID requireManage(UUID unitId) {
		UUID user = currentUser.require().id();
		unit(unitId);
		if (!authorization.can(user, Permission.MANAGE_CONFIGURATION, unitId)) {
			throw ApiException.forbidden();
		}
		return user;
	}

	record StatusInput(String key, String name, String phase, String category, boolean inSequence) {
		static StatusInput of(Status s) {
			return new StatusInput(s.key(), s.name(), s.phase(), s.category(), s.position() != null);
		}
	}

	record TransitionInput(String from, String to) {
	}

	/** Workflow vigente na unidade; {@code inherited} quando vem de uma unidade acima. */
	record UnitWorkflow(Workflow workflow, boolean inherited, OrgUnitRef source) {
	}
}
