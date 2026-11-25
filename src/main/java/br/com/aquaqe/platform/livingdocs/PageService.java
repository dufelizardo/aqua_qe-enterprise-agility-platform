package br.com.aquaqe.platform.livingdocs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.DocumentTemplates;
import br.com.aquaqe.platform.configuration.DocumentTemplates.Section;
import br.com.aquaqe.platform.configuration.DocumentTemplates.Template;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.livingdocs.DocumentRepository.PageRow;
import br.com.aquaqe.platform.livingdocs.DocumentRepository.VersionRow;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;
import tools.jackson.databind.ObjectMapper;

/**
 * Páginas de conhecimento por unidade organizacional (Proposta 07, Fase A; SSD 3.7.1). O espaço é a própria unidade
 * (KNOW-D01/D02). Acesso: quem lê a unidade pelas regras 1 a 7 lê tudo; quem tem papel abaixo dela lê só o que está
 * publicado (regra 9). Página livre publica ao salvar; página de template governado (regimento) passa por revisão,
 * aprovação e publicação, separadas (KNOW-D05).
 */
@Service
class PageService {

	static final int TITLE_MAX = 200;
	static final int SUMMARY_MAX = 300;
	static final int REASON_MAX = 500;
	/** O Regimento é independente do Wiki (2026-11-21): um por unidade, pelos endpoints próprios. */
	static final String REGIMENTO = "REGIMENTO";

	private final DocumentRepository repository;
	private final DocumentTemplates templates;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;
	private final UserDirectory users;
	private final ObjectMapper json;
	private final DomainEvents events;
	private final DocumentSections documentSections;

	PageService(DocumentRepository repository, DocumentTemplates templates, OrgUnits orgUnits,
			Authorization authorization, CurrentUser currentUser, UserDirectory users, ObjectMapper json,
			DomainEvents events, DocumentSections documentSections) {
		this.repository = repository;
		this.templates = templates;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
		this.users = users;
		this.json = json;
		this.events = events;
		this.documentSections = documentSections;
	}

	// ---------------------------------------------------------------------------------------------- leitura

	Map<String, Object> list(UUID orgUnitId, boolean archived) {
		UUID user = user();
		Access access = access(user, orgUnitId);
		if (access == Access.NONE || orgUnits.find(orgUnitId).isEmpty()) {
			throw ApiException.notFound();
		}
		List<PageRow> rows = repository.pagesOf(orgUnitId).stream()
			.filter(p -> !REGIMENTO.equals(p.type()))
			.filter(p -> access == Access.FULL ? p.archived() == archived : !p.archived() && p.publishedVersion() != null)
			.toList();
		Map<UUID, Template> byTemplate = new HashMap<>();
		List<Map<String, Object>> items = new ArrayList<>();
		for (PageRow row : rows) {
			Template template = byTemplate.computeIfAbsent(row.templateId(), this::template);
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", row.id());
			item.put("parentId", row.parentId());
			item.put("title", row.title());
			item.put("type", row.type());
			item.put("governed", template.governed());
			item.put("status", row.status());
			item.put("publishedVersion", row.publishedVersion());
			item.put("archived", row.archived());
			item.put("updatedAt", row.updatedAt());
			items.add(item);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("orgUnitId", orgUnitId);
		result.put("access", access.name());
		result.put("canEdit", access == Access.FULL && authorization.can(user, Permission.EDIT_KNOWLEDGE, orgUnitId));
		result.put("items", items);
		return result;
	}

	Map<String, Object> get(UUID id) {
		UUID user = user();
		PageRow page = readable(id, user);
		Access access = access(user, page.orgUnitId());
		int shown = access == Access.FULL ? page.currentVersion() : page.publishedVersion();
		return view(page, access, user, version(page, shown));
	}

	List<Map<String, Object>> versions(UUID id) {
		UUID user = user();
		PageRow page = fullyReadable(id, user);
		List<VersionRow> rows = repository.versions(page.id());
		Map<UUID, UserRef> people = users.find(rows.stream().map(VersionRow::authorId).filter(Objects::nonNull).toList());
		return rows.stream().map(v -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("number", v.number());
			item.put("summary", v.summary());
			item.put("author", person(people, v.authorId()));
			item.put("savedAt", v.savedAt());
			item.put("approved", Objects.equals(v.number(), page.approvedVersion()));
			item.put("published", Objects.equals(v.number(), page.publishedVersion()));
			return item;
		}).toList();
	}

	Map<String, Object> version(UUID id, int number) {
		UUID user = user();
		PageRow page = fullyReadable(id, user);
		return view(page, Access.FULL, user, repository.version(page.id(), number).orElseThrow(ApiException::notFound));
	}

	// ---------------------------------------------------------------------------------------------- escrita

	@Transactional
	Map<String, Object> create(UUID orgUnitId, Map<String, Object> body) {
		UUID user = user();
		Access access = access(user, orgUnitId);
		if (access == Access.NONE || orgUnits.find(orgUnitId).isEmpty()) {
			throw ApiException.notFound();
		}
		requireEdit(user, orgUnitId, access);
		String title = title(body.get("title"));
		if (body.get("type") != null && !"PAGE".equals(String.valueOf(body.get("type")))) {
			throw ApiException.validation("type", "o Wiki só tem páginas; o regimento fica em /org-units/{id}/regimento");
		}
		return insert(user, orgUnitId, "PAGE", title, parent(body.get("parentId"), orgUnitId, null));
	}

	/**
	 * Regimento da unidade (2026-11-21): um documento governado por unidade, independente do Wiki. Devolve o regimento
	 * ativo ({@code null} se não houver ou se quem lê só pela regra 9 ainda não tem versão publicada), o acesso e se o
	 * usuário pode criá-lo.
	 */
	Map<String, Object> regimento(UUID orgUnitId) {
		UUID user = user();
		Access access = access(user, orgUnitId);
		if (access == Access.NONE || orgUnits.find(orgUnitId).isEmpty()) {
			throw ApiException.notFound();
		}
		PageRow row = activeRegimento(orgUnitId)
			.filter(p -> access == Access.FULL || p.publishedVersion() != null)
			.orElse(null);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("orgUnitId", orgUnitId);
		result.put("access", access.name());
		result.put("canEdit", access == Access.FULL && authorization.can(user, Permission.EDIT_KNOWLEDGE, orgUnitId));
		result.put("regimento", row == null ? null : get(row.id()));
		return result;
	}

	@Transactional
	Map<String, Object> createRegimento(UUID orgUnitId, Map<String, Object> body) {
		UUID user = user();
		Access access = access(user, orgUnitId);
		var unit = orgUnits.find(orgUnitId);
		if (access == Access.NONE || unit.isEmpty()) {
			throw ApiException.notFound();
		}
		requireEdit(user, orgUnitId, access);
		if (activeRegimento(orgUnitId).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "REGIMENTO_EXISTS", "Esta unidade já tem regimento.");
		}
		Object given = body == null ? null : body.get("title");
		String title = given == null || String.valueOf(given).isBlank() ? "Regimento — " + unit.get().name() : title(given);
		return insert(user, orgUnitId, REGIMENTO, title, null);
	}

	private java.util.Optional<PageRow> activeRegimento(UUID orgUnitId) {
		return repository.pagesOf(orgUnitId).stream()
			.filter(p -> REGIMENTO.equals(p.type()) && !p.archived())
			.findFirst();
	}

	private Map<String, Object> insert(UUID user, UUID orgUnitId, String type, String title, UUID parentId) {
		Template template = templates.latest(type).orElseThrow(() -> ApiException.validation("type", "sem template"));
		boolean governed = template.governed();
		UUID id = repository.insertPage(orgUnitId, parentId, type, title, governed ? "DRAFT" : "PUBLISHED",
			template.id(), governed ? null : 1, user);
		List<Map<String, Object>> sections = template.sections().stream()
			.map(s -> Map.<String, Object>of("key", s.key(), "blocks", List.of()))
			.toList();
		repository.insertVersion(id, 1, json.writeValueAsString(sections), null, user);
		events.publish("knowledge_page.created", user, orgUnitId, aggregate(id, title), Map.of("type", type), null,
			Map.of("title", title, "type", type, "parentId", String.valueOf(parentId)));
		return get(id);
	}

	@Transactional
	Map<String, Object> save(UUID id, Map<String, Object> body) {
		UUID user = user();
		PageRow page = editable(id, user);
		Template template = template(page.templateId());
		if (template.governed() && Set.of("IN_REVIEW", "APPROVED").contains(page.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_EDITABLE",
				"O documento está em revisão ou aprovado. Volte-o para rascunho antes de editar.");
		}
		if (!(body.get("baseVersion") instanceof Number base)) {
			throw ApiException.validation("baseVersion", "obrigatório");
		}
		if (base.intValue() != page.currentVersion()) {
			throw ApiException.versionConflict();
		}
		String sections = json.writeValueAsString(sections(template, body.get("sections")));
		if (sections.getBytes(StandardCharsets.UTF_8).length > Blocks.MAX_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE",
				"O documento passa do limite de 1 MB por versão.");
		}
		String summary = optionalText(body.get("summary"), "summary", SUMMARY_MAX);
		int next = page.currentVersion() + 1;
		String status = template.governed() ? "DRAFT" : "PUBLISHED";
		Integer published = template.governed() ? page.publishedVersion() : Integer.valueOf(next);
		if (!repository.advance(page.id(), page.currentVersion(), status, published)) {
			throw ApiException.versionConflict();
		}
		repository.insertVersion(page.id(), next, sections, summary, user);
		events.publish("knowledge_page.updated", user, page.orgUnitId(), aggregate(page.id(), page.title()),
			Map.of("version", next), Map.of("version", page.currentVersion(), "status", page.status()),
			Map.of("version", next, "status", status));
		return get(page.id());
	}

	/** Ciclo do documento governado (KNOW-D05). Aprovação e publicação são passos separados. */
	@Transactional
	Map<String, Object> changeStatus(UUID id, Map<String, Object> body) {
		UUID user = user();
		PageRow page = fullyReadable(id, user);
		Template template = template(page.templateId());
		String to = body.get("status") == null ? "" : String.valueOf(body.get("status"));
		String from = page.status();
		if (!template.governed() || page.archived()) {
			throw notAllowed(from, to);
		}
		boolean canEdit = authorization.can(user, Permission.EDIT_KNOWLEDGE, page.orgUnitId());
		boolean canApprove = canApprove(user, page, template);
		Integer approved = page.approvedVersion();
		Integer published = page.publishedVersion();
		switch (from + ">" + to) {
			case "DRAFT>IN_REVIEW" -> require(canEdit);
			case "IN_REVIEW>DRAFT" -> require(canEdit || canApprove);
			case "IN_REVIEW>APPROVED" -> {
				require(canApprove);
				List<String> missing = missing(template, version(page, page.currentVersion()));
				if (!missing.isEmpty()) {
					throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_INCOMPLETE",
						"Preencha as seções obrigatórias antes de aprovar.", Map.of("missingSections", missing));
				}
				approved = page.currentVersion();
			}
			case "APPROVED>PUBLISHED" -> {
				require(canApprove);
				published = page.approvedVersion();
			}
			case "APPROVED>DRAFT" -> {
				require(canEdit);
				optionalText(body.get("comment"), "comment", REASON_MAX);
				if (body.get("comment") == null || String.valueOf(body.get("comment")).isBlank()) {
					throw ApiException.validation("comment", "obrigatório ao reabrir");
				}
			}
			default -> throw notAllowed(from, to);
		}
		repository.changeStatus(page.id(), to, approved, published);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("from", from);
		payload.put("to", to);
		if (body.get("comment") != null) {
			payload.put("comment", String.valueOf(body.get("comment")));
		}
		events.publish("knowledge_page.status_changed", user, page.orgUnitId(), aggregate(page.id(), page.title()),
			payload, Map.of("status", from), Map.of("status", to, "version", page.currentVersion()));
		return get(page.id());
	}

	/** Renomeia e move (a hierarquia de páginas fica dentro da unidade; nunca vira hierarquia organizacional). */
	@Transactional
	Map<String, Object> update(UUID id, Map<String, Object> body) {
		UUID user = user();
		PageRow page = editable(id, user);
		String title = body.containsKey("title") ? title(body.get("title")) : page.title();
		boolean moving = body.containsKey("parentId");
		if (moving && REGIMENTO.equals(page.type())) {
			throw ApiException.validation("parentId", "o regimento não fica dentro do Wiki");
		}
		UUID parentId = moving ? parent(body.get("parentId"), page.orgUnitId(), page.id()) : page.parentId();
		moving = moving && !Objects.equals(parentId, page.parentId());
		repository.changeMeta(page.id(), title, parentId, moving);
		if (moving) {
			events.publish("knowledge_page.moved", user, page.orgUnitId(), aggregate(page.id(), title), Map.of(),
				Map.of("parentId", String.valueOf(page.parentId())), Map.of("parentId", String.valueOf(parentId)));
		}
		if (!title.equals(page.title())) {
			events.publish("knowledge_page.updated", user, page.orgUnitId(), aggregate(page.id(), title), Map.of(),
				Map.of("title", page.title()), Map.of("title", title));
		}
		return get(page.id());
	}

	/**
	 * Reordena e move uma página dentro do Wiki da unidade (PROP-002): {@code beforeId} ou {@code afterId} apontam a
	 * irmã de referência; {@code parentId} (se presente, inclusive nulo = raiz) muda o nível, levando as subpáginas
	 * junto. Só muda a posição: não cria versão, não mexe no status e mantém o id (os links continuam valendo).
	 */
	@Transactional
	Map<String, Object> rank(UUID id, Map<String, Object> body) {
		UUID user = user();
		PageRow page = editable(id, user);
		if (REGIMENTO.equals(page.type())) {
			throw ApiException.validation("parentId", "o regimento não fica dentro do Wiki");
		}
		boolean moving = body.containsKey("parentId");
		UUID parentId = moving ? parent(body.get("parentId"), page.orgUnitId(), page.id()) : page.parentId();
		UUID beforeId = uuidOrNull(body.get("beforeId"), "beforeId");
		UUID afterId = uuidOrNull(body.get("afterId"), "afterId");
		if (beforeId != null && afterId != null) {
			throw ApiException.validation("beforeId", "informe beforeId ou afterId, não os dois");
		}
		if (beforeId == null && afterId == null && !moving) {
			throw ApiException.validation("beforeId", "informe beforeId, afterId ou parentId");
		}
		List<PageRow> siblings = repository.pagesOf(page.orgUnitId()).stream()
			.filter(p -> !p.archived() && !REGIMENTO.equals(p.type()) && !p.id().equals(page.id()))
			.filter(p -> Objects.equals(p.parentId(), parentId))
			.toList();
		double position;
		UUID anchorId = beforeId != null ? beforeId : afterId;
		if (anchorId == null) {
			position = siblings.isEmpty() ? page.position() : siblings.getLast().position() + 1000;
		}
		else {
			int index = -1;
			for (int i = 0; i < siblings.size(); i++) {
				if (siblings.get(i).id().equals(anchorId)) {
					index = i;
				}
			}
			if (index < 0) {
				throw ApiException.validation(beforeId != null ? "beforeId" : "afterId",
					"a página de referência precisa estar no mesmo nível");
			}
			double anchor = siblings.get(index).position();
			if (beforeId != null) {
				position = index == 0 ? anchor - 1000 : (siblings.get(index - 1).position() + anchor) / 2;
			}
			else {
				position = index == siblings.size() - 1 ? anchor + 1000 : (anchor + siblings.get(index + 1).position()) / 2;
			}
		}
		repository.reposition(page.id(), parentId, position);
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("parentId", String.valueOf(page.parentId()));
		before.put("position", page.position());
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("parentId", String.valueOf(parentId));
		after.put("position", position);
		events.publish("knowledge_page.moved", user, page.orgUnitId(), aggregate(page.id(), page.title()), Map.of(),
			before, after);
		return get(page.id());
	}

	private static UUID uuidOrNull(Object value, String field) {
		if (value == null || String.valueOf(value).isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(String.valueOf(value));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.validation(field, "valor inválido");
		}
	}

	@Transactional
	Map<String, Object> archive(UUID id, Map<String, Object> body) {
		UUID user = user();
		PageRow page = editable(id, user);
		if (repository.hasActiveChildren(page.id())) {
			throw new ApiException(HttpStatus.CONFLICT, "HAS_CHILDREN", "Arquive as subpáginas antes.");
		}
		String reason = optionalText(body == null ? null : body.get("reason"), "reason", REASON_MAX);
		repository.archive(page.id(), reason);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("archived", true);
		if (reason != null) {
			after.put("reason", reason);
		}
		events.publish("knowledge_page.archived", user, page.orgUnitId(), aggregate(page.id(), page.title()), Map.of(),
			Map.of("archived", false), after);
		return get(page.id());
	}

	@Transactional
	Map<String, Object> restore(UUID id) {
		UUID user = user();
		PageRow page = fullyReadable(id, user);
		requireEdit(user, page.orgUnitId(), Access.FULL);
		if (REGIMENTO.equals(page.type()) && activeRegimento(page.orgUnitId()).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "REGIMENTO_EXISTS", "Esta unidade já tem outro regimento ativo.");
		}
		// Se o pai continua arquivado, a página volta para a raiz do espaço.
		UUID parentId = page.parentId() == null ? null
				: repository.page(page.parentId()).filter(p -> !p.archived()).map(PageRow::id).orElse(null);
		repository.restore(page.id(), parentId);
		events.publish("knowledge_page.restored", user, page.orgUnitId(), aggregate(page.id(), page.title()), Map.of(),
			Map.of("archived", true), Map.of("archived", false));
		return get(page.id());
	}

	// ---------------------------------------------------------------------------------------------- apoio

	private enum Access {
		FULL, PUBLISHED, NONE
	}

	private UUID user() {
		return currentUser.require().id();
	}

	/** Regras 1 a 7 (tudo) e regra 9 (só o publicado) do SSD 2.1. */
	private Access access(UUID user, UUID orgUnitId) {
		if (authorization.readScope(user).contains(orgUnitId)) {
			return Access.FULL;
		}
		return authorization.knowledgeScope(user).contains(orgUnitId) ? Access.PUBLISHED : Access.NONE;
	}

	private PageRow readable(UUID id, UUID user) {
		PageRow page = repository.page(id).orElseThrow(ApiException::notFound);
		Access access = access(user, page.orgUnitId());
		if (access == Access.NONE
				|| (access == Access.PUBLISHED && (page.archived() || page.publishedVersion() == null))) {
			throw ApiException.notFound();
		}
		return page;
	}

	/** Rascunhos, histórico e ações exigem ler a unidade pelas regras 1 a 7. */
	private PageRow fullyReadable(UUID id, UUID user) {
		PageRow page = readable(id, user);
		if (access(user, page.orgUnitId()) != Access.FULL) {
			throw page.publishedVersion() == null ? ApiException.notFound() : ApiException.forbidden();
		}
		return page;
	}

	private PageRow editable(UUID id, UUID user) {
		PageRow page = readable(id, user);
		requireEdit(user, page.orgUnitId(), access(user, page.orgUnitId()));
		if (page.archived()) {
			throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_EDITABLE", "A página está arquivada.");
		}
		return page;
	}

	private void requireEdit(UUID user, UUID orgUnitId, Access access) {
		if (access != Access.FULL || !authorization.can(user, Permission.EDIT_KNOWLEDGE, orgUnitId)) {
			throw ApiException.forbidden();
		}
	}

	private static void require(boolean allowed) {
		if (!allowed) {
			throw ApiException.forbidden();
		}
	}

	private boolean canApprove(UUID user, PageRow page, Template template) {
		return template.governed() && template.approverRole() != null
				&& authorization.hasRole(user, template.approverRole(), page.orgUnitId());
	}

	private static ApiException notAllowed(String from, String to) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSITION_NOT_ALLOWED",
			"Não é possível passar de " + from + " para " + (to.isBlank() ? "(vazio)" : to) + ".");
	}

	private Template template(UUID id) {
		return templates.find(id).orElseThrow(() -> new IllegalStateException("Template inexistente: " + id));
	}

	private VersionRow version(PageRow page, int number) {
		return repository.version(page.id(), number).orElseThrow(ApiException::notFound);
	}

	private List<Map<String, Object>> sections(Template template, Object value) {
		return documentSections.fromBody(template, value);
	}

	private List<String> missing(Template template, VersionRow version) {
		return documentSections.missing(template, version.sectionsJson());
	}

	private Map<String, List<?>> blocksByKey(VersionRow version) {
		return documentSections.blocksByKey(version.sectionsJson());
	}

	private Map<String, Object> view(PageRow page, Access access, UUID user, VersionRow version) {
		Template template = template(page.templateId());
		Map<String, List<?>> blocks = blocksByKey(version);
		List<Map<String, Object>> sections = template.sections().stream().map(s -> {
			Map<String, Object> section = new LinkedHashMap<>();
			section.put("key", s.key());
			section.put("title", s.title());
			section.put("required", s.required());
			section.put("blocks", blocks.getOrDefault(s.key(), List.of()));
			return section;
		}).toList();
		Map<UUID, UserRef> people = users.find(version.authorId() == null ? List.of() : List.of(version.authorId()));
		boolean full = access == Access.FULL;
		boolean canEdit = full && authorization.can(user, Permission.EDIT_KNOWLEDGE, page.orgUnitId());
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("id", page.id());
		view.put("orgUnitId", page.orgUnitId());
		view.put("parentId", page.parentId());
		view.put("title", page.title());
		view.put("type", page.type());
		view.put("templateName", template.name());
		view.put("governed", template.governed());
		view.put("approverRole", template.approverRole());
		view.put("status", full ? page.status() : "PUBLISHED");
		view.put("currentVersion", full ? page.currentVersion() : page.publishedVersion());
		view.put("approvedVersion", full ? page.approvedVersion() : null);
		view.put("publishedVersion", page.publishedVersion());
		view.put("versionNumber", version.number());
		view.put("summary", version.summary());
		view.put("author", person(people, version.authorId()));
		view.put("savedAt", version.savedAt());
		view.put("archived", page.archived());
		view.put("archiveReason", page.archiveReason());
		view.put("access", access.name());
		view.put("canEdit", canEdit);
		view.put("canApprove", full && canApprove(user, page, template));
		view.put("missingSections", full ? missing(template, version) : List.of());
		view.put("sections", sections);
		return view;
	}

	private static Map<String, Object> person(Map<UUID, UserRef> people, UUID id) {
		UserRef ref = id == null ? null : people.get(id);
		if (ref == null) {
			return null;
		}
		Map<String, Object> person = new LinkedHashMap<>();
		person.put("id", ref.id());
		person.put("name", ref.name());
		return person;
	}

	/** Pai precisa ser página não arquivada da mesma unidade e não pode ser a própria página nem uma descendente. */
	private UUID parent(Object value, UUID orgUnitId, UUID self) {
		if (value == null || String.valueOf(value).isBlank()) {
			return null;
		}
		UUID parentId;
		try {
			parentId = UUID.fromString(String.valueOf(value));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.validation("parentId", "valor inválido");
		}
		PageRow parent = repository.page(parentId)
			.filter(p -> p.orgUnitId().equals(orgUnitId) && !p.archived() && !REGIMENTO.equals(p.type()))
			.orElseThrow(() -> ApiException.validation("parentId", "página pai inexistente nesta unidade"));
		for (UUID cursor = parent.id(); cursor != null;
				cursor = repository.page(cursor).map(PageRow::parentId).orElse(null)) {
			if (cursor.equals(self)) {
				throw ApiException.validation("parentId", "a página não pode ficar abaixo dela mesma");
			}
		}
		return parent.id();
	}

	private static String title(Object value) {
		String title = value == null ? "" : String.valueOf(value).strip();
		if (title.isEmpty() || title.length() > TITLE_MAX) {
			throw ApiException.validation("title", "obrigatório, até " + TITLE_MAX + " caracteres");
		}
		return title;
	}

	private static String optionalText(Object value, String field, int max) {
		if (value == null || String.valueOf(value).isBlank()) {
			return null;
		}
		String text = String.valueOf(value).strip();
		if (text.length() > max) {
			throw ApiException.validation(field, "até " + max + " caracteres");
		}
		return text;
	}

	private static Aggregate aggregate(UUID id, String title) {
		return new Aggregate("KNOWLEDGE_PAGE", id, title);
	}
}
