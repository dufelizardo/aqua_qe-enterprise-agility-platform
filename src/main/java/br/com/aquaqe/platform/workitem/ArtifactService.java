package br.com.aquaqe.platform.workitem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.Status;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.organization.OrgUnits.OrgUnitSummary;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.shared.PageRequest;
import br.com.aquaqe.platform.shared.PageResponse;
import br.com.aquaqe.platform.workitem.ArtifactRepository.Filter;
import br.com.aquaqe.platform.workitem.ArtifactRepository.Row;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.ArtifactViews.ContextView;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import tools.jackson.databind.ObjectMapper;

/**
 * Regras dos artefatos (SSD 3.4) e do escopo entre unidades (SSD 2.1). Leitura: regras 1, 2, 3, 6 e 7; criação e
 * edição: matriz do SSD 2 na unidade do artefato e regras 4 e 5 para o pai em outro ramo.
 */
@Service
class ArtifactService {

	static final int TITLE_MAX = 200;
	static final int SUMMARY_MAX = 300;
	static final Set<String> SIZES = Set.of("XS", "S", "M", "L", "XL");
	private static final Set<String> USER_FIELDS = Set.of("ownerId", "technicalLeadId", "qaOwnerId", "assigneeId");
	private static final Set<String> PATCHABLE = Set.of("title", "description", "ownerId", "technicalLeadId",
		"qaOwnerId", "assigneeId", "size", "customFields", "parentId", "blocked", "blockedReason", "version");
	static final int BLOCKED_REASON_MAX = 500;
	private static final int PEOPLE_LIMIT = 20;

	private final ArtifactRepository repository;
	private final ArtifactConfiguration configuration;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final UserDirectory users;
	private final ObjectMapper json;
	private final DomainEvents events;
	private final ApplicationEventPublisher publisher;

	ArtifactService(ArtifactRepository repository, ArtifactConfiguration configuration, OrgUnits orgUnits,
			Authorization authorization, CurrentUser currentUser, UserDirectory users, ObjectMapper json,
			DomainEvents events, ApplicationEventPublisher publisher) {
		this.repository = repository;
		this.configuration = configuration;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.users = users;
		this.json = json;
		this.events = events;
		this.publisher = publisher;
	}

	// ---------------------------------------------------------------------------------------------- leitura

	ArtifactView get(String ref) {
		Caller caller = caller();
		Row row = readable(ref, caller);
		return views(List.of(row)).getFirst().withCanEdit(canEdit(caller, row, typeOf(row)));
	}

	/**
	 * Pessoas que podem ser escolhidas como Responsável, Technical Lead, QA ou executor do artefato: as que têm papel
	 * vigente na unidade dele, nas unidades acima e nas de baixo. Só quem edita o artefato consulta.
	 */
	List<ArtifactViews.Person> people(String ref, String query) {
		Caller caller = caller();
		Row row = readable(ref, caller);
		requireEdit(caller, row, typeOf(row));
		Set<UUID> units = new java.util.HashSet<>(orgUnits.lineage(row.orgUnitId()));
		units.addAll(orgUnits.subtrees(List.of(row.orgUnitId())));
		return users.search(authorization.peopleIn(units), query, PEOPLE_LIMIT).stream()
			.map(ArtifactViews.Person::of)
			.toList();
	}

	PageResponse<ArtifactView> list(UUID workspaceId, Map<String, String> query, PageRequest page) {
		Caller caller = caller();
		if (workspaceId == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "WORKSPACE_REQUIRED", "Escolha um workspace para continuar.");
		}
		Set<UUID> workspace = orgUnits.subtrees(List.of(workspaceId));
		Filter filter = new Filter(workspace, typeIds(query.get("type"), query.get("level")),
			statusIds(query.get("statusKey"), query.get("statusCategory")), uuid(query, "orgUnitId"),
			uuid(query, "parentId"), uuid(query, "ownerId"), uuid(query, "assigneeId"), query.get("q"),
			Boolean.parseBoolean(query.getOrDefault("archived", "false")), "rank".equals(query.get("sort")));
		List<Row> rows = repository.search(filter, caller.scope(), page.offset(), page.size());
		return PageResponse.of(views(rows), page, repository.count(filter, caller.scope()));
	}

	/** Itens não arquivados de um nível na subárvore da unidade, que o usuário lê (regras 1, 2 e 6), para os quadros. */
	List<ArtifactView> itemsOfLevel(UUID orgUnitId, String level, int limit) {
		Caller caller = caller();
		Filter filter = new Filter(orgUnits.subtrees(List.of(orgUnitId)), typeIds(null, level), null, null, null, null,
			null, null, false, true);
		return views(repository.search(filter, caller.scope(), 0, limit));
	}

	/** Itens não arquivados do nível, nos status informados, na subárvore da unidade, de todos os usuários (WIP). */
	long countForBoard(UUID orgUnitId, String level, Collection<UUID> statusIds) {
		Filter filter = new Filter(orgUnits.subtrees(List.of(orgUnitId)), typeIds(null, level), statusIds, null, null,
			null, null, null, false, false);
		return repository.count(filter, null);
	}

	boolean inBoardScope(UUID artifactId, UUID orgUnitId) {
		return repository.withinUnits(artifactId, orgUnits.subtrees(List.of(orgUnitId)));
	}

	List<ArtifactView> children(String ref, boolean archived) {
		Row parent = readable(ref, caller());
		// Todo filho é descendente de um artefato legível: a regra 2 o torna legível também.
		return views(repository.children(parent.id(), archived));
	}

	/** Contexto herdado (SSD 3.4): o resumo de cada ancestral, da raiz até o pai (SSD 2.1, regra 3). */
	ContextView context(String ref) {
		Row row = readable(ref, caller());
		List<Row> ancestors = repository.rows(row.ancestorIds());
		Map<UUID, Status> statuses = configuration.statuses(ancestors.stream().map(Row::statusId).toList());
		List<ContextView.Ancestor> items = new ArrayList<>();
		for (Row a : ancestors) {
			Status status = statuses.get(a.statusId());
			items.add(new ContextView.Ancestor(a.key(), typeOf(a).key(), a.title(), status == null ? null : status.name(),
				summary(a.description())));
		}
		// RNs e status de documentos entram com os módulos correspondentes.
		return new ContextView(items, List.of());
	}

	// ---------------------------------------------------------------------------------------------- escrita

	@Transactional
	ArtifactView create(Map<String, Object> body) {
		Caller caller = caller();
		String title = requireTitle(body.get("title"));
		ArtifactType type = configuration.type(text(body, "type"))
			.orElseThrow(() -> ApiException.validation("type", "tipo de artefato inexistente"));
		if (body.containsKey("status")) {
			throw ApiException.validation("status", "o status inicial é sempre o primeiro do workflow");
		}
		UUID unitId = Optional.ofNullable(uuidField(body, "orgUnitId"))
			.orElseThrow(() -> ApiException.validation("orgUnitId", "obrigatório"));
		OrgUnitSummary unit = orgUnits.find(unitId)
			.orElseThrow(() -> ApiException.validation("orgUnitId", "unidade inexistente"));
		if (!unit.active()) {
			throw ApiException.validation("orgUnitId", "unidade desativada");
		}
		if (!authorization.can(caller.userId(), Permission.editAt(type.level()), unitId)) {
			throw ApiException.forbidden();
		}
		// Tudo nasce na Squad (bug #66, 2026-11-21): a unidade de FL1 aceita artefatos de qualquer Flight Level; nas demais,
		// o nível do artefato continua tendo de ser o da unidade.
		if (!type.level().equals(unit.flightLevel()) && !"FL1".equals(unit.flightLevel())) {
			throw unprocessable("INVALID_ORG_UNIT_TYPE",
				"Um artefato de " + type.level() + " fica em uma Squad (FL1) ou em uma unidade de nível " + type.level() + ".");
		}
		Row parent = parentFor(type, uuidField(body, "parentId"), unitId, caller, null);
		Map<String, UUID> people = people(body, null);

		UUID id = UUID.randomUUID();
		String key = type.keyPrefix() + "-" + repository.nextNumber(type.keyPrefix());
		String path = (parent == null ? "/" : parent.path()) + id + "/";
		Row row = new Row(id, key, type.id(), parent == null ? null : parent.id(), path, unitId,
			configuration.initialStatus(type.key()).id(), title, text(body, "description"), people.get("ownerId"),
			people.get("technicalLeadId"), people.get("qaOwnerId"), people.get("assigneeId"),
			size(body.get("size")), customFields(body.get("customFields")), false, 0, null, null, 0, false, null);
		repository.insert(row, caller.userId());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("key", key);
		payload.put("artifactType", type.key());
		payload.put("parentId", row.parentId());
		events.publish("artifact.created", caller.userId(), unitId, new Aggregate("ARTIFACT", id, key), payload, null,
			auditable(row));
		publisher.publishEvent(new ArtifactCreated(id, key, type.key(), unitId, caller.userId()));
		return views(List.of(repository.find(id).orElseThrow())).getFirst();
	}

	/** {@code PATCH} com semântica de JSON Merge Patch (SSD 1.5): campo ausente não muda; {@code null} limpa. */
	@Transactional
	ArtifactView update(String ref, Map<String, Object> body) {
		Caller caller = caller();
		Row current = readable(ref, caller);
		ArtifactType type = typeOf(current);
		requireEdit(caller, current, type);
		if (body.containsKey("status")) {
			throw ApiException.validation("status", "o status muda pelas transições do workflow");
		}
		for (String field : body.keySet()) {
			if (!PATCHABLE.contains(field)) {
				throw ApiException.validation(field, "não pode ser alterado");
			}
		}
		if (!(body.get("version") instanceof Number version)) {
			throw ApiException.validation("version", "obrigatório");
		}

		Row parent = current.parentId() == null ? null : repository.find(current.parentId()).orElse(null);
		String path = current.path();
		if (body.containsKey("parentId")) {
			UUID newParentId = uuidField(body, "parentId");
			if (!Objects.equals(newParentId, current.parentId())) {
				parent = parentFor(type, newParentId, current.orgUnitId(), caller, current);
				path = (parent == null ? "/" : parent.path()) + current.id() + "/";
			}
		}
		Map<String, UUID> people = people(body, current);
		Row updated = new Row(current.id(), current.key(), current.typeId(), parent == null ? null : parent.id(), path,
			current.orgUnitId(), current.statusId(),
			body.containsKey("title") ? requireTitle(body.get("title")) : current.title(),
			body.containsKey("description") ? text(body, "description") : current.description(),
			people.get("ownerId"), people.get("technicalLeadId"), people.get("qaOwnerId"), people.get("assigneeId"),
			body.containsKey("size") ? size(body.get("size")) : current.size(),
			body.containsKey("customFields") ? customFields(body.get("customFields")) : current.customFields(),
			current.archived(), current.version(), null, null, 0, blocked(body, current), blockedReason(body, current));
		if (!repository.update(updated, version.intValue())) {
			throw ApiException.versionConflict();
		}
		if (!path.equals(current.path())) {
			repository.movePaths(current.path(), path);
		}
		Map<String, Object> before = auditable(current);
		Map<String, Object> after = auditable(updated);
		before.keySet().removeIf(k -> Objects.equals(before.get(k), after.get(k)));
		after.keySet().retainAll(before.keySet());
		events.publish("artifact.updated", caller.userId(), current.orgUnitId(),
			new Aggregate("ARTIFACT", current.id(), current.key()), Map.of("key", current.key()), before, after);
		return views(List.of(repository.find(current.id()).orElseThrow())).getFirst().withCanEdit(true);
	}

	/**
	 * Reposiciona o artefato na ordenação (SSD 3.4, {@code POST /artifacts/{artifact}/rank}): imediatamente antes de
	 * {@code beforeId} ou depois de {@code afterId}. A ordem é global, então qualquer lista ordenada por {@code rank}
	 * (backlog, colunas do quadro) a respeita.
	 */
	@Transactional
	ArtifactView rank(String ref, UUID beforeId, UUID afterId) {
		Caller caller = caller();
		Row row = readable(ref, caller);
		requireEdit(caller, row, typeOf(row));
		if ((beforeId == null) == (afterId == null)) {
			throw ApiException.validation("beforeId", "informe beforeId ou afterId");
		}
		UUID neighborId = beforeId != null ? beforeId : afterId;
		if (neighborId.equals(row.id())) {
			throw ApiException.validation(beforeId != null ? "beforeId" : "afterId", "use outro item como referência");
		}
		Row neighbor = repository.find(neighborId)
			.filter(n -> repository.readable(n.id(), caller.scope()))
			.orElseThrow(() -> ApiException.validation(beforeId != null ? "beforeId" : "afterId", "item não encontrado"));
		java.math.BigDecimal anchor = repository.rankOf(neighbor.id());
		java.math.BigDecimal step = java.math.BigDecimal.valueOf(1000);
		java.math.BigDecimal rank = repository.neighborRank(anchor, row.id(), beforeId != null)
			.map(other -> other.add(anchor).divide(java.math.BigDecimal.valueOf(2)))
			.orElseGet(() -> beforeId != null ? anchor.subtract(step) : anchor.add(step));
		repository.setRank(row.id(), rank);
		return views(List.of(repository.find(row.id()).orElseThrow())).getFirst();
	}

	/** Arquiva (SSD 3.4): não apaga; exige que não haja filhos ativos. */
	@Transactional
	void archive(String ref) {
		Caller caller = caller();
		Row row = readable(ref, caller);
		requireEdit(caller, row, typeOf(row));
		if (row.archived()) {
			return;
		}
		if (row.childCount() > 0) {
			throw new ApiException(HttpStatus.CONFLICT, "HAS_CHILDREN", "Arquive ou mova os itens filhos antes.");
		}
		repository.archive(row.id());
		events.publish("artifact.archived", caller.userId(), row.orgUnitId(), new Aggregate("ARTIFACT", row.id(), row.key()),
			Map.of("key", row.key()), Map.of("archived", false), Map.of("archived", true));
	}

	/** Campos do artefato que vão para a trilha de auditoria (antes e depois). */
	private Map<String, Object> auditable(Row r) {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("title", r.title());
		values.put("description", r.description());
		values.put("parentId", r.parentId() == null ? null : r.parentId().toString());
		values.put("orgUnitId", r.orgUnitId().toString());
		values.put("ownerId", r.ownerId() == null ? null : r.ownerId().toString());
		values.put("technicalLeadId", r.technicalLeadId() == null ? null : r.technicalLeadId().toString());
		values.put("qaOwnerId", r.qaOwnerId() == null ? null : r.qaOwnerId().toString());
		values.put("assigneeId", r.assigneeId() == null ? null : r.assigneeId().toString());
		values.put("size", r.size());
		values.put("customFields", parseCustomFields(r.customFields()));
		values.put("blocked", r.blocked());
		values.put("blockedReason", r.blockedReason());
		return values;
	}

	// ---------------------------------------------------------------------------------------------- regras

	/**
	 * Valida o pai (SSD 3.4): tipo permitido, pai obrigatório quando o tipo exige, sem ciclo, e unidades ligadas quando o
	 * pai está em outro ramo (SSD 2.1, regra 4). Um pai que o usuário não lê só é aceito se as unidades estão ligadas
	 * (regra 5); fora disso, ele é tratado como inexistente, para não revelar que existe.
	 */
	private Row parentFor(ArtifactType type, UUID parentId, UUID unitId, Caller caller, Row self) {
		if (parentId == null) {
			if (type.parentRequired()) {
				throw unprocessable("INVALID_PARENT_TYPE", "Um " + type.name() + " precisa de um item pai.");
			}
			return null;
		}
		Row parent = repository.find(parentId).orElseThrow(() -> ApiException.validation("parentId", "item não encontrado"));
		boolean parentReadable = repository.readable(parent.id(), caller.scope());
		List<UUID> unitLineage = orgUnits.lineage(unitId);
		boolean linked = unitLineage.contains(parent.orgUnitId())
				|| orgUnits.anyServes(unitLineage, orgUnits.lineage(parent.orgUnitId()));
		if (!linked && !parentReadable) {
			throw ApiException.validation("parentId", "item não encontrado");
		}
		ArtifactType parentType = typeOf(parent);
		if (!type.parentTypes().contains(parentType.key())) {
			throw unprocessable("INVALID_PARENT_TYPE",
				"Um " + type.name() + " não pode ficar sob um " + parentType.name() + ".");
		}
		if (parent.archived()) {
			throw ApiException.validation("parentId", "item arquivado");
		}
		if (self != null && parent.path().startsWith(self.path())) {
			throw ApiException.validation("parentId", "o item não pode ficar sob ele mesmo ou um descendente");
		}
		if (!linked) {
			throw unprocessable("ORG_UNITS_NOT_LINKED",
				"A unidade escolhida não atende à unidade do item pai. Peça o vínculo ao administrador.");
		}
		return parent;
	}

	void requireEdit(Caller caller, Row row, ArtifactType type) {
		if (!canEdit(caller, row, type)) {
			throw ApiException.forbidden();
		}
	}

	private boolean canEdit(Caller caller, Row row, ArtifactType type) {
		return authorization.can(caller.userId(), Permission.editAt(type.level()), row.orgUnitId());
	}

	/** Fora do escopo de leitura responde 404, igual a inexistente (SSD 2.1, regra 7). */
	Row readable(String ref, Caller caller) {
		Row row = resolve(ref).orElseThrow(ApiException::notFound);
		if (!repository.readable(row.id(), caller.scope())) {
			throw ApiException.notFound();
		}
		return row;
	}

	/** {@code {artifact}} aceita o id ou a chave legível (SSD 1.3). */
	private Optional<Row> resolve(String ref) {
		try {
			return repository.find(UUID.fromString(ref));
		} catch (IllegalArgumentException notUuid) {
			return repository.findByKey(ref.toUpperCase(java.util.Locale.ROOT));
		}
	}

	Caller caller() {
		UUID userId = currentUser.require().id();
		return new Caller(userId, authorization.readScope(userId));
	}

	private Map<String, UUID> people(Map<String, Object> body, Row current) {
		Map<String, UUID> result = new HashMap<>();
		result.put("ownerId", current == null ? null : current.ownerId());
		result.put("technicalLeadId", current == null ? null : current.technicalLeadId());
		result.put("qaOwnerId", current == null ? null : current.qaOwnerId());
		result.put("assigneeId", current == null ? null : current.assigneeId());
		for (String field : USER_FIELDS) {
			if (body.containsKey(field)) {
				result.put(field, uuidField(body, field));
			}
		}
		Set<UUID> informed = result.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
		Map<UUID, UserRef> found = users.find(informed);
		for (Map.Entry<String, UUID> entry : result.entrySet()) {
			if (entry.getValue() != null && !found.containsKey(entry.getValue())) {
				throw ApiException.validation(entry.getKey(), "usuário inexistente");
			}
		}
		return result;
	}

	private Set<UUID> typeIds(String typeKey, String level) {
		if (typeKey == null && level == null) {
			return null;
		}
		if (level != null && !Set.of("FL1", "FL2", "FL3").contains(level)) {
			throw ApiException.validation("level", "use FL1, FL2 ou FL3");
		}
		return configuration.types().stream()
			.filter(t -> typeKey == null || t.key().equals(typeKey))
			.filter(t -> level == null || t.level().equals(level))
			.map(ArtifactType::id)
			.collect(Collectors.toSet());
	}

	private Set<UUID> statusIds(String key, String category) {
		if (key == null && category == null) {
			return null;
		}
		if (category != null && !Set.of("TODO", "IN_PROGRESS", "DONE").contains(category)) {
			throw ApiException.validation("statusCategory", "use TODO, IN_PROGRESS ou DONE");
		}
		return configuration.statusIds(key, category);
	}

	ArtifactType typeOf(Row row) {
		return configuration.type(row.typeId()).orElseThrow();
	}

	// ---------------------------------------------------------------------------------------------- visões

	List<ArtifactView> views(List<Row> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<UUID, ArtifactType> types = configuration.types().stream()
			.collect(Collectors.toMap(ArtifactType::id, t -> t));
		Map<UUID, Status> statuses = configuration.statuses(rows.stream().map(Row::statusId).collect(Collectors.toSet()));
		Map<UUID, Row> parents = new HashMap<>();
		repository.rows(rows.stream().map(Row::parentId).filter(Objects::nonNull).distinct().toList())
			.forEach(p -> parents.put(p.id(), p));
		Map<UUID, OrgUnitSummary> units = new HashMap<>();
		rows.stream().map(Row::orgUnitId).distinct()
			.forEach(id -> orgUnits.find(id).ifPresent(u -> units.put(id, u)));
		Set<UUID> people = rows.stream()
			.flatMap(r -> Stream.of(r.ownerId(), r.technicalLeadId(), r.qaOwnerId(), r.assigneeId()))
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<UUID, UserRef> names = users.find(people);

		List<ArtifactView> result = new ArrayList<>();
		for (Row r : rows) {
			ArtifactType type = types.get(r.typeId());
			Row p = r.parentId() == null ? null : parents.get(r.parentId());
			result.add(new ArtifactView(r.id(), r.key(), type.key(), type.level(), r.title(), r.description(),
				p == null ? null : new ArtifactViews.ParentRef(p.id(), p.key(), types.get(p.typeId()).key(), p.title()),
				ArtifactViews.UnitRef.of(units.get(r.orgUnitId())), ArtifactViews.StatusRef.of(statuses.get(r.statusId())),
				person(names, r.ownerId()), person(names, r.technicalLeadId()),
				person(names, r.qaOwnerId()), person(names, r.assigneeId()),
				r.size(), parseCustomFields(r.customFields()), r.childCount(), r.archived(), r.version(), r.createdAt(),
				r.updatedAt(), r.blocked(), r.blockedReason(), null));
		}
		return result;
	}

	private static ArtifactViews.Person person(Map<UUID, UserRef> names, UUID id) {
		return id == null ? null : ArtifactViews.Person.of(names.get(id));
	}

	// ---------------------------------------------------------------------------------------------- campos

	/** Marca de bloqueado (Proposta 04, KAN-009): {@code blocked} liga e desliga; desligar limpa o motivo. */
	private static boolean blocked(Map<String, Object> body, Row current) {
		if (!body.containsKey("blocked")) {
			return current.blocked();
		}
		if (!(body.get("blocked") instanceof Boolean value)) {
			throw ApiException.validation("blocked", "use true ou false");
		}
		return value;
	}

	private static String blockedReason(Map<String, Object> body, Row current) {
		if (!blocked(body, current)) {
			return null;
		}
		String reason = body.containsKey("blockedReason") ? text(body, "blockedReason") : current.blockedReason();
		if (reason == null || reason.isBlank()) {
			return null;
		}
		if (reason.strip().length() > BLOCKED_REASON_MAX) {
			throw ApiException.validation("blockedReason", "no máximo " + BLOCKED_REASON_MAX + " caracteres");
		}
		return reason.strip();
	}

	private static String requireTitle(Object value) {
		String title = value instanceof String s ? s.strip() : "";
		if (title.isEmpty()) {
			throw ApiException.validation("title", "obrigatório");
		}
		if (title.length() > TITLE_MAX) {
			throw ApiException.validation("title", "no máximo " + TITLE_MAX + " caracteres");
		}
		return title;
	}

	private static String size(Object value) {
		if (value == null) {
			return null;
		}
		if (!(value instanceof String s) || !SIZES.contains(s)) {
			throw ApiException.validation("size", "use XS, S, M, L ou XL");
		}
		return s;
	}

	private String customFields(Object value) {
		if (value == null) {
			return "{}";
		}
		if (!(value instanceof Map<?, ?> map)) {
			throw ApiException.validation("customFields", "deve ser um objeto");
		}
		return json.writeValueAsString(map);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseCustomFields(String value) {
		return value == null ? Map.of() : json.readValue(value, LinkedHashMap.class);
	}

	private static String text(Map<String, Object> body, String field) {
		Object value = body.get(field);
		if (value != null && !(value instanceof String)) {
			throw ApiException.validation(field, "deve ser texto");
		}
		return (String) value;
	}

	private static UUID uuidField(Map<String, Object> body, String field) {
		Object value = body.get(field);
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value.toString());
		} catch (IllegalArgumentException e) {
			throw ApiException.validation(field, "identificador inválido");
		}
	}

	private static UUID uuid(Map<String, String> query, String name) {
		String value = query.get(name);
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw ApiException.validation(name, "identificador inválido");
		}
	}

	private static String summary(String description) {
		if (description == null) {
			return null;
		}
		return description.length() <= SUMMARY_MAX ? description : description.substring(0, SUMMARY_MAX);
	}

	private static ApiException unprocessable(String code, String detail) {
		return new ApiException(HttpStatus.valueOf(422), code, detail);
	}

	record Caller(UUID userId, Collection<UUID> scope) {
	}
}
