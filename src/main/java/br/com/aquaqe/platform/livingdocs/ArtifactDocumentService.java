package br.com.aquaqe.platform.livingdocs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.configuration.DocumentTemplates;
import br.com.aquaqe.platform.configuration.DocumentTemplates.Template;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.livingdocs.DocumentRepository.ArtifactDocRow;
import br.com.aquaqe.platform.livingdocs.DocumentRepository.VersionRow;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.ArtifactCreated;
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Documentos da Iniciativa e da Feature (SSD 3.7; PRD-RF-018 a 020): PRD, SAD, SSD, UX e QA, criados com o artefato em
 * {@code PENDING}. Ler segue a leitura do artefato; editar, a edição do artefato no nível dele (SSD 2); aprovar, o papel
 * aprovador do template na linhagem da unidade. Ciclo: {@code PENDING} → {@code IN_PROGRESS} (primeira gravação) →
 * {@code APPROVED}; reabrir volta a {@code IN_PROGRESS}, com comentário. Os gates que exigem documento aprovado ficam
 * para a entrega dos gates (SSD 5).
 */
@Service
class ArtifactDocumentService {

	/** Tipos de documento, na ordem das abas. */
	static final List<String> DOCUMENT_TYPES = List.of("PRD", "SAD", "SSD", "UX", "QA");
	/** Tipos de artefato que têm documentos (SSD 3.7). */
	static final Set<String> ARTIFACT_TYPES = Set.of("INITIATIVE", "FEATURE");
	private static final int SUMMARY_MAX = 300;
	private static final int COMMENT_MAX = 500;

	private final DocumentRepository repository;
	private final DocumentTemplates templates;
	private final DocumentSections sections;
	private final Artifacts artifacts;
	private final Authorization authorization;
	private final UserDirectory users;
	private final DomainEvents events;

	ArtifactDocumentService(DocumentRepository repository, DocumentTemplates templates, DocumentSections sections,
			Artifacts artifacts, Authorization authorization, UserDirectory users, DomainEvents events) {
		this.repository = repository;
		this.templates = templates;
		this.sections = sections;
		this.artifacts = artifacts;
		this.authorization = authorization;
		this.users = users;
		this.events = events;
	}

	/** Os cinco documentos nascem com a Iniciativa e a Feature, na mesma transação. */
	@EventListener
	void on(ArtifactCreated event) {
		if (ARTIFACT_TYPES.contains(event.artifactType())) {
			for (String type : DOCUMENT_TYPES) {
				Template template = templates.latest(type)
					.orElseThrow(() -> new IllegalStateException("Template inexistente: " + type));
				repository.insertArtifactDocument(event.artifactId(), type, template.id(), event.actorId());
			}
		}
	}

	// ---------------------------------------------------------------------------------------------- leitura

	Map<String, Object> list(String ref) {
		ArtifactRef artifact = withDocuments(ref);
		boolean canEdit = canEdit(artifact);
		List<Map<String, Object>> items = new ArrayList<>();
		for (ArtifactDocRow doc : ordered(repository.documentsOf(artifact.id()))) {
			Template template = template(doc);
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("type", doc.type());
			item.put("name", template.name());
			item.put("status", doc.status());
			item.put("currentVersion", doc.currentVersion());
			item.put("approvedVersion", doc.approvedVersion());
			item.put("updatedAt", doc.currentVersion() == 0 ? null : doc.updatedAt());
			item.put("canEdit", canEdit);
			item.put("canApprove", canApprove(artifact, template));
			items.add(item);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("artifactKey", artifact.key());
		result.put("items", items);
		return result;
	}

	Map<String, Object> get(String ref, String type) {
		ArtifactRef artifact = withDocuments(ref);
		ArtifactDocRow doc = document(artifact, type);
		return view(artifact, doc, current(doc));
	}

	List<Map<String, Object>> versions(String ref, String type) {
		ArtifactRef artifact = withDocuments(ref);
		ArtifactDocRow doc = document(artifact, type);
		List<VersionRow> rows = repository.versions(doc.id());
		Map<UUID, UserRef> people = users.find(rows.stream().map(VersionRow::authorId).filter(id -> id != null).toList());
		return rows.stream().map(v -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("number", v.number());
			item.put("summary", v.summary());
			item.put("author", person(people, v.authorId()));
			item.put("savedAt", v.savedAt());
			item.put("approved", doc.approvedVersion() != null && doc.approvedVersion() == v.number());
			return item;
		}).toList();
	}

	Map<String, Object> version(String ref, String type, int number) {
		ArtifactRef artifact = withDocuments(ref);
		ArtifactDocRow doc = document(artifact, type);
		return view(artifact, doc, repository.version(doc.id(), number).orElseThrow(ApiException::notFound));
	}

	// ----------------------------------------------------------------------------------------------- escrita

	@Transactional
	Map<String, Object> save(String ref, String type, Map<String, Object> body) {
		ArtifactRef artifact = withDocuments(ref);
		ArtifactDocRow doc = document(artifact, type);
		artifacts.requireEdit(artifact);
		if (artifact.archived()) {
			throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_EDITABLE", "O artefato está arquivado.");
		}
		if ("APPROVED".equals(doc.status())) {
			throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_EDITABLE",
				"O documento está aprovado. Reabra-o antes de editar.");
		}
		if (!(body.get("baseVersion") instanceof Number base)) {
			throw ApiException.validation("baseVersion", "obrigatório");
		}
		if (base.intValue() != doc.currentVersion()) {
			throw ApiException.versionConflict();
		}
		Template template = template(doc);
		String content = sections.toJson(sections.fromBody(template, body.get("sections")));
		if (content.getBytes(StandardCharsets.UTF_8).length > Blocks.MAX_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE",
				"O documento passa do limite de 1 MB por versão.");
		}
		String summary = optionalText(body.get("summary"), "summary", SUMMARY_MAX);
		int next = doc.currentVersion() + 1;
		if (!repository.advance(doc.id(), doc.currentVersion(), "IN_PROGRESS", null)) {
			throw ApiException.versionConflict();
		}
		repository.insertVersion(doc.id(), next, content, summary, artifact.actorId());
		events.publish("document.saved", artifact.actorId(), artifact.orgUnitId(), aggregate(artifact, doc),
			Map.of("artifactKey", artifact.key(), "documentType", doc.type(), "version", next),
			Map.of("version", doc.currentVersion(), "status", doc.status()),
			Map.of("version", next, "status", "IN_PROGRESS"));
		return get(ref, type);
	}

	/** Aprovar ({@code APPROVED}) ou reabrir ({@code IN_PROGRESS}, com comentário). */
	@Transactional
	Map<String, Object> changeStatus(String ref, String type, Map<String, Object> body) {
		ArtifactRef artifact = withDocuments(ref);
		ArtifactDocRow doc = document(artifact, type);
		Template template = template(doc);
		String to = body.get("status") == null ? "" : String.valueOf(body.get("status"));
		String from = doc.status();
		String comment = optionalText(body.get("comment"), "comment", COMMENT_MAX);
		if (artifact.archived()) {
			throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_NOT_EDITABLE", "O artefato está arquivado.");
		}
		Integer approved;
		String event;
		switch (from + ">" + to) {
			case "IN_PROGRESS>APPROVED" -> {
				require(canApprove(artifact, template));
				List<String> missing = sections.missing(template, current(doc).sectionsJson());
				if (!missing.isEmpty()) {
					throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOCUMENT_INCOMPLETE",
						"Preencha as seções obrigatórias antes de aprovar.", Map.of("missingSections", missing));
				}
				approved = doc.currentVersion();
				event = "document.approved";
			}
			case "APPROVED>IN_PROGRESS" -> {
				artifacts.requireEdit(artifact);
				if (comment == null) {
					throw ApiException.validation("comment", "obrigatório ao reabrir");
				}
				approved = null;
				event = "document.reopened";
			}
			default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSITION_NOT_ALLOWED",
				"Não é possível passar de " + from + " para " + (to.isBlank() ? "(vazio)" : to) + ".");
		}
		repository.changeStatus(doc.id(), to, approved, null);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("artifactKey", artifact.key());
		payload.put("documentType", doc.type());
		payload.put("version", doc.currentVersion());
		if (comment != null) {
			payload.put("comment", comment);
		}
		events.publish(event, artifact.actorId(), artifact.orgUnitId(), aggregate(artifact, doc), payload,
			Map.of("status", from), Map.of("status", to));
		return get(ref, type);
	}

	// ------------------------------------------------------------------------------------------------- apoio

	/** O artefato legível e de um tipo com documentos; senão {@code 404}. */
	private ArtifactRef withDocuments(String ref) {
		ArtifactRef artifact = artifacts.readable(ref);
		if (!ARTIFACT_TYPES.contains(artifact.type())) {
			throw ApiException.notFound();
		}
		return artifact;
	}

	private ArtifactDocRow document(ArtifactRef artifact, String type) {
		return repository.documentOf(artifact.id(), type).orElseThrow(ApiException::notFound);
	}

	private static List<ArtifactDocRow> ordered(List<ArtifactDocRow> rows) {
		return rows.stream().sorted(Comparator.comparingInt(d -> DOCUMENT_TYPES.indexOf(d.type()))).toList();
	}

	/** Versão atual; o documento ainda sem gravação é a "versão 0", vazia. */
	private VersionRow current(ArtifactDocRow doc) {
		if (doc.currentVersion() == 0) {
			return new VersionRow(0, null, null, null, null);
		}
		return repository.version(doc.id(), doc.currentVersion()).orElseThrow(ApiException::notFound);
	}

	private Template template(ArtifactDocRow doc) {
		return templates.find(doc.templateId())
			.orElseThrow(() -> new IllegalStateException("Template inexistente: " + doc.templateId()));
	}

	private boolean canEdit(ArtifactRef artifact) {
		return !artifact.archived() && artifacts.canEdit(artifact);
	}

	private boolean canApprove(ArtifactRef artifact, Template template) {
		return !artifact.archived() && template.approverRole() != null
				&& authorization.hasRole(artifact.actorId(), template.approverRole(), artifact.orgUnitId());
	}

	private Map<String, Object> view(ArtifactRef artifact, ArtifactDocRow doc, VersionRow version) {
		Template template = template(doc);
		Map<String, List<?>> blocks = sections.blocksByKey(version.sectionsJson());
		List<Map<String, Object>> content = template.sections().stream().map(s -> {
			Map<String, Object> section = new LinkedHashMap<>();
			section.put("key", s.key());
			section.put("title", s.title());
			section.put("required", s.required());
			section.put("blocks", blocks.getOrDefault(s.key(), List.of()));
			return section;
		}).toList();
		Map<UUID, UserRef> people = users.find(version.authorId() == null ? List.of() : List.of(version.authorId()));
		Map<String, Object> view = new LinkedHashMap<>();
		view.put("artifactKey", artifact.key());
		view.put("type", doc.type());
		view.put("name", template.name());
		view.put("approverRole", template.approverRole());
		view.put("status", doc.status());
		view.put("currentVersion", doc.currentVersion());
		view.put("approvedVersion", doc.approvedVersion());
		view.put("versionNumber", version.number());
		view.put("summary", version.summary());
		view.put("author", person(people, version.authorId()));
		view.put("savedAt", version.savedAt());
		view.put("canEdit", canEdit(artifact));
		view.put("canApprove", canApprove(artifact, template));
		view.put("missingSections", sections.missing(template, version.sectionsJson()));
		view.put("sections", content);
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

	private static void require(boolean allowed) {
		if (!allowed) {
			throw ApiException.forbidden();
		}
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

	private static Aggregate aggregate(ArtifactRef artifact, ArtifactDocRow doc) {
		return new Aggregate("DOCUMENT", doc.id(), artifact.key() + "/" + doc.type());
	}
}
