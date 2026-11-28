package br.com.aquaqe.platform.governance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.StatusTransition;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.TransitionScope;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.governance.GateRepository.Decision;
import br.com.aquaqe.platform.governance.GateRepository.GateRow;
import br.com.aquaqe.platform.governance.GateRepository.NewRule;
import br.com.aquaqe.platform.governance.GateRepository.RuleRow;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workflow.TransitionGuard.FailedRule;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Gates por transição (SSD 5; #78): configuração (só quem gere configuração), avaliação das regras pelos
 * {@link GateRuleEvaluator}s, painel de gates do artefato e decisões das regras de aprovação.
 */
@Service
class GateService {

	static final int MAX_RULES = 20;
	static final int NAME_MAX = 120;
	static final int MESSAGE_MAX = 300;
	static final int COMMENT_MAX = 1000;
	/** Tipos do SSD 5.2 que ainda não têm avaliador (dependem de módulos por fazer). */
	static final Map<String, String> PENDING_TYPES = Map.of("BUSINESS_CASE_VALIDATED",
		"depende do business case da Iniciativa (módulo portfolio), que ainda não existe");

	private final GateRepository repository;
	private final Map<String, GateRuleEvaluator> evaluators;
	private final ArtifactConfiguration configuration;
	private final Artifacts artifacts;
	private final Authorization authorization;
	private final OrgUnits orgUnits;
	private final CurrentUser currentUser;
	/** Quantos nomes de aprovadores o painel mostra (#14). */
	static final int APPROVERS_SHOWN = 10;

	private final UserDirectory users;
	private final DomainEvents events;

	GateService(GateRepository repository, List<GateRuleEvaluator> evaluators, ArtifactConfiguration configuration,
			Artifacts artifacts, Authorization authorization, OrgUnits orgUnits, CurrentUser currentUser,
			UserDirectory users, DomainEvents events) {
		this.repository = repository;
		this.evaluators = evaluators.stream().collect(Collectors.toMap(GateRuleEvaluator::type, Function.identity()));
		this.configuration = configuration;
		this.artifacts = artifacts;
		this.authorization = authorization;
		this.orgUnits = orgUnits;
		this.currentUser = currentUser;
		this.users = users;
		this.events = events;
	}

	// ------------------------------------------------------------------------------------------ configuração

	/** Gates configurados; com {@code workflowId}, só os das transições desse workflow (SSD 3.6). */
	@Transactional(readOnly = true)
	List<GateSummary> list(UUID workflowId) {
		currentUser.require();
		return repository.gates().stream()
			.filter(g -> workflowId == null || configuration.transition(g.transitionId())
				.map(t -> t.workflowId().equals(workflowId)).orElse(false))
			.map(g -> new GateSummary(g.id(), g.transitionId(), g.name(), repository.ruleCount(g.id())))
			.toList();
	}

	/** Decisões registradas no artefato, da mais recente para a mais antiga (SSD 3.6). */
	@Transactional(readOnly = true)
	List<ApprovalView> approvals(String ref) {
		ArtifactRef artifact = artifacts.readable(ref);
		Map<UUID, String> names = new HashMap<>();
		return repository.decisions(artifact.id()).stream()
			.map(d -> new ApprovalView(d.id(), d.ruleId(), d.decision(), d.comment(),
				new Person(d.decidedBy(), name(d.decidedBy(), names)), d.decidedAt()))
			.toList();
	}

	@Transactional(readOnly = true)
	GateView get(UUID transitionId) {
		requireConfiguration();
		TransitionScope scope = scope(transitionId);
		GateRow gate = repository.gateOf(transitionId).orElseThrow(ApiException::notFound);
		return view(gate, scope);
	}

	@Transactional
	GateView put(UUID transitionId, Map<String, Object> body) {
		UUID user = requireConfiguration();
		TransitionScope scope = scope(transitionId);
		String name = text(body == null ? null : body.get("name"), "name", NAME_MAX);
		Object raw = body.get("rules");
		if (!(raw instanceof List<?> list) || list.isEmpty() || list.size() > MAX_RULES) {
			throw ApiException.validation("rules", "de 1 a " + MAX_RULES + " regras");
		}
		List<NewRule> rules = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			String field = "rules[" + i + "]";
			if (!(list.get(i) instanceof Map<?, ?> item)) {
				throw ApiException.validation(field, "regra inválida");
			}
			String type = text(item.get("type"), field + ".type", 40);
			if (PENDING_TYPES.containsKey(type)) {
				throw ApiException.validation(field + ".type", type + " " + PENDING_TYPES.get(type));
			}
			GateRuleEvaluator evaluator = evaluators.get(type);
			if (evaluator == null) {
				throw ApiException.validation(field + ".type", "tipo de regra desconhecido");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> params = item.get("params") instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of();
			Map<String, Object> clean = evaluator.validate(params, scope, field + ".params");
			rules.add(new NewRule(type, clean, text(item.get("message"), field + ".message", MESSAGE_MAX)));
		}
		boolean existed = repository.gateOf(transitionId).isPresent();
		UUID gateId = repository.save(transitionId, name, rules, user);
		events.publish(existed ? "gate.updated" : "gate.created", user, orgUnits.rootId(),
			new Aggregate("GATE", gateId, null), Map.of("transitionId", transitionId.toString(), "name", name,
				"rules", rules.size()));
		return view(repository.gateOf(transitionId).orElseThrow(), scope);
	}

	@Transactional
	void delete(UUID transitionId) {
		UUID user = requireConfiguration();
		GateRow gate = repository.gateOf(transitionId).orElseThrow(ApiException::notFound);
		repository.delete(transitionId);
		events.publish("gate.deleted", user, orgUnits.rootId(), new Aggregate("GATE", gate.id(), null),
			Map.of("transitionId", transitionId.toString(), "name", gate.name()));
	}

	// ------------------------------------------------------------------------------------------ avaliação

	/** Regras não atendidas do gate da transição; vazio sem gate ou com tudo atendido. */
	List<FailedRule> evaluate(ArtifactRef artifact, StatusTransition transition) {
		Optional<GateRow> gate = repository.gateOf(transition.id());
		if (gate.isEmpty()) {
			return List.of();
		}
		Supplier<ArtifactView> view = lazyView(artifact);
		List<FailedRule> failed = new ArrayList<>();
		for (RuleRow rule : repository.rules(gate.get().id())) {
			GateRuleEvaluator evaluator = evaluators.get(rule.type());
			boolean ok = evaluator != null
					&& evaluator.satisfied(rule.params(), new GateRuleEvaluator.Context(artifact, view, rule.id()));
			if (!ok) {
				failed.add(new FailedRule(rule.id().toString(), rule.type(), category(rule.type()), rule.message(),
					rule.params(), gate.get().name()));
			}
		}
		return failed;
	}

	/** Painel de gates (UX/UI 6.4): os gates das transições que saem do status atual, regra a regra. */
	@Transactional(readOnly = true)
	List<PanelGate> panel(String ref) {
		ArtifactRef artifact = artifacts.readable(ref);
		List<StatusTransition> transitions = configuration.transitionsFrom(artifact.statusId());
		Map<UUID, GateRow> gates = repository.gatesOf(transitions.stream().map(StatusTransition::id).toList());
		Supplier<ArtifactView> view = lazyView(artifact);
		List<PanelGate> result = new ArrayList<>();
		Map<UUID, String> names = new HashMap<>();
		for (StatusTransition transition : transitions) {
			GateRow gate = gates.get(transition.id());
			if (gate == null) {
				continue;
			}
			List<PanelRule> rules = new ArrayList<>();
			for (RuleRow rule : repository.rules(gate.id())) {
				GateRuleEvaluator evaluator = evaluators.get(rule.type());
				boolean ok = evaluator != null
						&& evaluator.satisfied(rule.params(), new GateRuleEvaluator.Context(artifact, view, rule.id()));
				LastDecision last = null;
				boolean canDecide = false;
				Approvers approvers = null;
				if ("ROLE_APPROVAL".equals(rule.type())) {
					approvers = approvers(String.valueOf(rule.params().get("role")), artifact.orgUnitId());
					Optional<Decision> decision = repository.lastDecision(artifact.id(), rule.id(), artifact.statusId());
					if (decision.isPresent()) {
						Decision d = decision.get();
						last = new LastDecision(d.decision(), d.comment(), new Person(d.decidedBy(), name(d.decidedBy(), names)),
							d.decidedAt());
					}
					canDecide = !artifact.archived() && authorization.hasRole(artifact.actorId(),
						String.valueOf(rule.params().get("role")), artifact.orgUnitId());
				}
				rules.add(new PanelRule(rule.id(), rule.type(), category(rule.type()), rule.message(), rule.params(), ok,
					last, canDecide, approvers == null ? null : approvers.people(),
					approvers == null ? null : approvers.count()));
			}
			long satisfied = rules.stream().filter(PanelRule::satisfied).count();
			result.add(new PanelGate(gate.id(), gate.name(), transition.id(),
				new StatusRef(transition.to().key(), transition.to().name()), satisfied, rules.size(), rules));
		}
		return result;
	}

	/** Aprova ou rejeita uma regra {@code ROLE_APPROVAL} de um gate do status atual. */
	@Transactional
	List<PanelGate> decide(String ref, Map<String, Object> body) {
		ArtifactRef artifact = artifacts.readable(ref);
		if (artifact.archived()) {
			throw new ApiException(HttpStatus.valueOf(422), "TRANSITION_NOT_ALLOWED", "Item arquivado não recebe aprovação.");
		}
		UUID ruleId;
		try {
			ruleId = UUID.fromString(String.valueOf(body == null ? null : body.get("gateRuleId")));
		} catch (IllegalArgumentException e) {
			throw ApiException.validation("gateRuleId", "obrigatório");
		}
		Object decisionValue = body.get("decision");
		if (!"APPROVED".equals(decisionValue) && !"REJECTED".equals(decisionValue)) {
			throw ApiException.validation("decision", "use APPROVED ou REJECTED");
		}
		String decision = (String) decisionValue;
		Object commentValue = body.get("comment");
		String comment = commentValue == null || String.valueOf(commentValue).isBlank() ? null
				: String.valueOf(commentValue).strip();
		if (comment != null && comment.length() > COMMENT_MAX) {
			throw ApiException.validation("comment", "até " + COMMENT_MAX + " caracteres");
		}
		if ("REJECTED".equals(decision) && comment == null) {
			throw ApiException.validation("comment", "explique a rejeição");
		}
		RuleRow rule = approvalRule(artifact, ruleId)
			.orElseThrow(() -> new ApiException(HttpStatus.valueOf(422), "APPROVAL_NOT_APPLICABLE",
				"A regra não é uma aprovação dos gates de saída do status atual."));
		String role = String.valueOf(rule.params().get("role"));
		if (!authorization.hasRole(artifact.actorId(), role, artifact.orgUnitId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED",
				"Esta aprovação é de quem tem o papel " + role + " na unidade do item ou acima.");
		}
		repository.decide(artifact.id(), rule.id(), artifact.statusId(), decision, comment, artifact.actorId());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("key", artifact.key());
		payload.put("ruleId", rule.id().toString());
		payload.put("role", role);
		payload.put("decision", decision);
		events.publish("approval.decided", artifact.actorId(), artifact.orgUnitId(),
			new Aggregate("ARTIFACT", artifact.id(), artifact.key()), payload, null, Map.of("decision", decision));
		return panel(ref);
	}

	// ------------------------------------------------------------------------------------------ apoio

	private Optional<RuleRow> approvalRule(ArtifactRef artifact, UUID ruleId) {
		List<UUID> transitionIds = configuration.transitionsFrom(artifact.statusId()).stream()
			.map(StatusTransition::id).toList();
		return repository.gatesOf(transitionIds).values().stream()
			.flatMap(g -> repository.rules(g.id()).stream())
			.filter(r -> r.id().equals(ruleId) && "ROLE_APPROVAL".equals(r.type()))
			.findFirst();
	}

	private Supplier<ArtifactView> lazyView(ArtifactRef artifact) {
		ArtifactView[] cache = new ArtifactView[1];
		return () -> {
			if (cache[0] == null) {
				cache[0] = artifacts.view(artifact.id());
			}
			return cache[0];
		};
	}

	private String category(String type) {
		GateRuleEvaluator evaluator = evaluators.get(type);
		return evaluator == null ? "VALIDATION" : evaluator.category();
	}

	/**
	 * Quem tem o papel aprovador na unidade do item ou acima, ativo, em ordem de nome: até {@link #APPROVERS_SHOWN}
	 * nomes e o total (#14; REV-09). O {@code PLATFORM_ADMIN} também aprova, mas não entra na lista.
	 */
	private Approvers approvers(String role, UUID orgUnitId) {
		java.util.Set<UUID> holders = authorization.roleHoldersInLineage(role, orgUnitId);
		List<UserRef> active = users.search(holders, "", Math.max(holders.size(), 1));
		List<Person> shown = active.stream().limit(APPROVERS_SHOWN).map(u -> new Person(u.id(), u.name())).toList();
		return new Approvers(shown, active.size());
	}

	private record Approvers(List<Person> people, int count) {
	}

	private String name(UUID id, Map<UUID, String> cache) {
		return cache.computeIfAbsent(id, key -> {
			UserRef user = users.find(List.of(key)).get(key);
			return user == null ? null : user.name();
		});
	}

	private TransitionScope scope(UUID transitionId) {
		return configuration.transition(transitionId).orElseThrow(ApiException::notFound);
	}

	private UUID requireConfiguration() {
		UUID user = currentUser.require().id();
		if (!authorization.can(user, Permission.MANAGE_CONFIGURATION, orgUnits.rootId())) {
			throw ApiException.forbidden();
		}
		return user;
	}

	private GateView view(GateRow gate, TransitionScope scope) {
		List<RuleView> rules = repository.rules(gate.id()).stream()
			.map(r -> new RuleView(r.id(), r.type(), category(r.type()), r.params(), r.message()))
			.toList();
		StatusTransition t = scope.transition();
		return new GateView(gate.id(), gate.name(), t.id(), new StatusRef(t.from().key(), t.from().name()),
			new StatusRef(t.to().key(), t.to().name()), scope.workflowName(), scope.artifactTypes(), rules,
			gate.updatedAt());
	}

	private static String text(Object value, String field, int max) {
		if (!(value instanceof String s) || s.isBlank() || s.strip().length() > max) {
			throw ApiException.validation(field, "obrigatório, até " + max + " caracteres");
		}
		return s.strip();
	}

	record StatusRef(String key, String name) {
	}

	record Person(UUID id, String name) {
	}

	record GateSummary(UUID id, UUID transitionId, String name, long rules) {
	}

	record RuleView(UUID id, String type, String category, Map<String, Object> params, String message) {
	}

	record GateView(UUID id, String name, UUID transitionId, StatusRef from, StatusRef to, String workflow,
			List<String> artifactTypes, List<RuleView> rules, java.time.Instant updatedAt) {
	}

	record LastDecision(String decision, String comment, Person by, java.time.Instant at) {
	}

	record ApprovalView(UUID id, UUID gateRuleId, String decision, String comment, Person decidedBy,
			java.time.Instant decidedAt) {
	}

	/**
	 * Regra no painel. Nas de aprovação, {@code approvers} traz até 10 pessoas com o papel e {@code approverCount}, o
	 * total (#14); nas demais, os dois vêm nulos.
	 */
	record PanelRule(UUID ruleId, String type, String category, String message, Map<String, Object> params,
			boolean satisfied, LastDecision lastDecision, boolean canDecide, List<Person> approvers,
			Integer approverCount) {
	}

	record PanelGate(UUID gateId, String name, UUID transitionId, StatusRef to, long satisfied, int total,
			List<PanelRule> rules) {
	}
}
