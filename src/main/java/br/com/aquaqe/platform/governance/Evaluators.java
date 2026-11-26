package br.com.aquaqe.platform.governance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.TransitionScope;
import br.com.aquaqe.platform.livingdocs.ArtifactDocuments;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.Artifacts;

/** Avaliadores dos tipos de regra do MVP (SSD 5.2). */
final class Evaluators {

	/** Tipos de artefato que têm os documentos PRD, SAD, SSD, UX e QA (SSD 3.7). */
	static final Set<String> TYPES_WITH_DOCUMENTS = Set.of("INITIATIVE", "FEATURE");
	static final List<String> DOCUMENT_TYPES = List.of("PRD", "SAD", "SSD", "UX", "QA");

	private Evaluators() {
	}

	static String text(Map<String, Object> params, String key, String field, int max) {
		Object value = params == null ? null : params.get(key);
		if (!(value instanceof String s) || s.isBlank() || s.strip().length() > max) {
			throw ApiException.validation(field + "." + key, "obrigatório, até " + max + " caracteres");
		}
		return s.strip();
	}

	static String documentType(Map<String, Object> params, TransitionScope scope, String field) {
		String type = text(params, "documentType", field, 10);
		if (!DOCUMENT_TYPES.contains(type)) {
			throw ApiException.validation(field + ".documentType", "use PRD, SAD, SSD, UX ou QA");
		}
		if (!TYPES_WITH_DOCUMENTS.containsAll(scope.artifactTypes())) {
			throw ApiException.validation(field + ".documentType",
				"o workflow " + scope.workflowName() + " vale para tipos sem documentos; só Iniciativa e Feature têm");
		}
		return type;
	}

	/** {@code REQUIRED_FIELD}: o campo do artefato está preenchido. */
	@Component
	static class RequiredField implements GateRuleEvaluator {

		static final Set<String> FIELDS = Set.of("description", "ownerId", "technicalLeadId", "qaOwnerId", "assigneeId",
			"size");
		static final Set<String> NOT_YET = Set.of("acceptanceCriteria", "businessRuleRefs");

		@Override
		public String type() {
			return "REQUIRED_FIELD";
		}

		@Override
		public Map<String, Object> validate(Map<String, Object> params, TransitionScope scope, String field) {
			String name = text(params, "field", field, 80);
			if (NOT_YET.contains(name)) {
				throw ApiException.validation(field + ".field", name + " ainda não existe nos artefatos");
			}
			boolean custom = name.startsWith("customFields.") && name.length() > "customFields.".length();
			if (!FIELDS.contains(name) && !custom) {
				throw ApiException.validation(field + ".field",
					"use description, ownerId, technicalLeadId, qaOwnerId, assigneeId, size ou customFields.<nome>");
			}
			return Map.of("field", name);
		}

		@Override
		public boolean satisfied(Map<String, Object> params, Context context) {
			String name = String.valueOf(params.get("field"));
			ArtifactView a = context.view().get();
			Object value = switch (name) {
				case "description" -> a.description();
				case "ownerId" -> a.owner();
				case "technicalLeadId" -> a.technicalLead();
				case "qaOwnerId" -> a.qaOwner();
				case "assigneeId" -> a.assignee();
				case "size" -> a.size();
				default -> a.customFields() == null ? null : a.customFields().get(name.substring("customFields.".length()));
			};
			return value != null && !(value instanceof String s && s.isBlank());
		}
	}

	/** {@code DOCUMENT_SECTION_FILLED}: a seção está preenchida na versão atual do documento. */
	@Component
	static class DocumentSectionFilled implements GateRuleEvaluator {

		private final ArtifactDocuments documents;

		DocumentSectionFilled(ArtifactDocuments documents) {
			this.documents = documents;
		}

		@Override
		public String type() {
			return "DOCUMENT_SECTION_FILLED";
		}

		@Override
		public Map<String, Object> validate(Map<String, Object> params, TransitionScope scope, String field) {
			String type = documentType(params, scope, field);
			String section = text(params, "sectionKey", field, 60);
			if (!documents.sectionKeys(type).contains(section)) {
				throw ApiException.validation(field + ".sectionKey",
					"o " + type + " não tem essa seção; use " + String.join(", ", documents.sectionKeys(type)));
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("documentType", type);
			result.put("sectionKey", section);
			return result;
		}

		@Override
		public boolean satisfied(Map<String, Object> params, Context context) {
			return documents.state(context.artifact().id(), String.valueOf(params.get("documentType")))
				.map(doc -> doc.filledSections().contains(String.valueOf(params.get("sectionKey"))))
				.orElse(false);
		}
	}

	/** {@code DOCUMENT_APPROVED}: o documento está aprovado. Reabrir o documento volta a bloquear. */
	@Component
	static class DocumentApproved implements GateRuleEvaluator {

		private final ArtifactDocuments documents;

		DocumentApproved(ArtifactDocuments documents) {
			this.documents = documents;
		}

		@Override
		public String type() {
			return "DOCUMENT_APPROVED";
		}

		@Override
		public Map<String, Object> validate(Map<String, Object> params, TransitionScope scope, String field) {
			return Map.of("documentType", documentType(params, scope, field));
		}

		@Override
		public boolean satisfied(Map<String, Object> params, Context context) {
			return documents.state(context.artifact().id(), String.valueOf(params.get("documentType")))
				.map(doc -> "APPROVED".equals(doc.status()))
				.orElse(false);
		}
	}

	/** {@code MIN_CHILDREN}: ao menos {@code min} filhos ativos do tipo. */
	@Component
	static class MinChildren implements GateRuleEvaluator {

		private final Artifacts artifacts;
		private final ArtifactConfiguration configuration;

		MinChildren(Artifacts artifacts, ArtifactConfiguration configuration) {
			this.artifacts = artifacts;
			this.configuration = configuration;
		}

		@Override
		public String type() {
			return "MIN_CHILDREN";
		}

		@Override
		public Map<String, Object> validate(Map<String, Object> params, TransitionScope scope, String field) {
			String child = text(params, "childType", field, 40);
			if (configuration.type(child).isEmpty()) {
				throw ApiException.validation(field + ".childType", "tipo de artefato desconhecido");
			}
			Object min = params.get("min");
			if (!(min instanceof Number n) || n.intValue() < 1 || n.intValue() > 100 || n.doubleValue() != n.intValue()) {
				throw ApiException.validation(field + ".min", "número inteiro de 1 a 100");
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("childType", child);
			result.put("min", n.intValue());
			return result;
		}

		@Override
		public boolean satisfied(Map<String, Object> params, Context context) {
			int min = ((Number) params.get("min")).intValue();
			return artifacts.activeChildren(context.artifact().id(), String.valueOf(params.get("childType"))) >= min;
		}
	}

	/** {@code ROLE_APPROVAL}: a última decisão, no status atual, de alguém com o papel é {@code APPROVED}. */
	@Component
	static class RoleApproval implements GateRuleEvaluator {

		static final Set<String> ROLES = Set.of("PLATFORM_ADMIN", "BUSINESS_OWNER", "PRODUCT_MANAGER", "TECH_LEAD",
			"UX_DESIGNER", "QA_ENGINEER", "SQUAD_MEMBER");

		private final GateRepository repository;

		RoleApproval(GateRepository repository) {
			this.repository = repository;
		}

		@Override
		public String type() {
			return "ROLE_APPROVAL";
		}

		@Override
		public String category() {
			return "APPROVAL";
		}

		@Override
		public Map<String, Object> validate(Map<String, Object> params, TransitionScope scope, String field) {
			String role = text(params, "role", field, 40);
			if (!ROLES.contains(role)) {
				throw ApiException.validation(field + ".role", "papel desconhecido");
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("role", role);
			Object label = params.get("label");
			if (label != null && !String.valueOf(label).isBlank()) {
				result.put("label", text(params, "label", field, 60));
			}
			return result;
		}

		@Override
		public boolean satisfied(Map<String, Object> params, Context context) {
			return repository.lastDecision(context.artifact().id(), context.ruleId(), context.artifact().statusId())
				.map(d -> "APPROVED".equals(d.decision()))
				.orElse(false);
		}
	}
}
