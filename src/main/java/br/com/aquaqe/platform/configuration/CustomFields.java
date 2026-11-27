package br.com.aquaqe.platform.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.CustomField;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;

import tools.jackson.databind.ObjectMapper;

/**
 * Catálogo de campos customizados por tipo de artefato (#17; REV-12). Vale na organização inteira e só o administrador
 * altera. A chave e o tipo não mudam depois de criados, para não invalidar valores já gravados; o rótulo, as opções e a
 * ordem mudam. Remover um campo citado por um gate é recusado ({@link CustomFieldUsage}).
 */
@Service
class CustomFields {

	static final Set<String> TYPES = Set.of("TEXT", "NUMBER", "DATE", "BOOLEAN", "SELECT");
	static final Pattern KEY = Pattern.compile("[a-z][a-zA-Z0-9]{0,39}");
	static final int LABEL_MAX = 80;
	static final int OPTION_MAX = 80;
	static final int MAX_OPTIONS = 30;
	static final int MAX_FIELDS = 30;

	private final JdbcClient jdbc;
	private final ObjectMapper json;
	private final ConfigurationRepository repository;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final OrgUnits orgUnits;
	private final DomainEvents events;
	private final List<CustomFieldUsage> usages;

	CustomFields(JdbcClient jdbc, ObjectMapper json, ConfigurationRepository repository, CurrentUser currentUser,
			Authorization authorization, OrgUnits orgUnits, DomainEvents events, List<CustomFieldUsage> usages) {
		this.jdbc = jdbc;
		this.json = json;
		this.repository = repository;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.orgUnits = orgUnits;
		this.events = events;
		this.usages = usages;
	}

	@Transactional(readOnly = true)
	List<CustomField> list(String typeKey) {
		currentUser.require();
		if (typeKey != null) {
			type(typeKey);
		}
		return repository.customFields(typeKey);
	}

	/** Declara um campo no tipo; vai para o fim da lista. */
	@Transactional
	CustomField create(String typeKey, Map<String, Object> body) {
		UUID user = requireAdmin();
		ArtifactType type = type(typeKey);
		Map<String, Object> input = body == null ? Map.of() : body;
		String key = asText(input.get("key"));
		if (key == null || !KEY.matcher(key).matches()) {
			throw ApiException.validation("key", "comece com letra minúscula; só letras e números, até 40 (ex.: canalDeVenda)");
		}
		String fieldType = asText(input.get("type"));
		if (fieldType == null || !TYPES.contains(fieldType)) {
			throw ApiException.validation("type", "use TEXT, NUMBER, DATE, BOOLEAN ou SELECT");
		}
		String label = label(input.get("label"));
		List<String> options = options(fieldType, input.get("options"));
		List<CustomField> existing = repository.customFields(typeKey);
		if (existing.size() >= MAX_FIELDS) {
			throw ApiException.validation("key", "o tipo já tem " + MAX_FIELDS + " campos");
		}
		int position = existing.stream().mapToInt(CustomField::position).max().orElse(0) + 1;
		UUID id = UUID.randomUUID();
		try {
			jdbc.sql("""
					insert into configuration.custom_field (id, type_id, key, label, field_type, options, position, created_by)
					values (:id, :type, :key, :label, :fieldType, cast(:options as jsonb), :position, :user)
					""")
				.param("id", id).param("type", type.id()).param("key", key).param("label", label)
				.param("fieldType", fieldType).param("options", json.writeValueAsString(options))
				.param("position", position).param("user", user)
				.update();
		} catch (DuplicateKeyException e) {
			throw ApiException.validation("key", "já existe um campo " + key + " em " + type.name());
		}
		CustomField created = find(id).orElseThrow();
		events.publish("custom_field.created", user, orgUnits.rootId(), new Aggregate("CUSTOM_FIELD", id, label),
			payload(created), null, snapshot(created));
		return created;
	}

	/** Muda rótulo, opções (só {@code SELECT}) e ordem ({@code position}). Chave e tipo não mudam. */
	@Transactional
	CustomField update(UUID id, Map<String, Object> body) {
		UUID user = requireAdmin();
		CustomField current = find(id).orElseThrow(ApiException::notFound);
		Map<String, Object> input = body == null ? Map.of() : body;
		for (String field : input.keySet()) {
			if (!Set.of("label", "options", "position").contains(field)) {
				throw ApiException.validation(field, "não pode ser alterado; a chave e o tipo ficam como foram criados");
			}
		}
		String label = input.containsKey("label") ? label(input.get("label")) : current.label();
		List<String> options = input.containsKey("options") ? options(current.type(), input.get("options"))
			: current.options();
		int position = current.position();
		if (input.containsKey("position")) {
			if (!(input.get("position") instanceof Number n) || n.intValue() < 1) {
				throw ApiException.validation("position", "número a partir de 1");
			}
			position = n.intValue();
		}
		jdbc.sql("""
				update configuration.custom_field
				   set label = :label, options = cast(:options as jsonb), position = :position, updated_at = now()
				 where id = :id
				""")
			.param("id", id).param("label", label).param("options", json.writeValueAsString(options))
			.param("position", position)
			.update();
		CustomField updated = find(id).orElseThrow();
		events.publish("custom_field.updated", user, orgUnits.rootId(), new Aggregate("CUSTOM_FIELD", id, label),
			payload(updated), snapshot(current), snapshot(updated));
		return updated;
	}

	/** Remove a declaração. Os valores já gravados nos artefatos ficam, mas não aparecem nem podem ser alterados. */
	@Transactional
	void remove(UUID id) {
		UUID user = requireAdmin();
		CustomField current = find(id).orElseThrow(ApiException::notFound);
		List<String> inUse = new ArrayList<>();
		usages.forEach(u -> inUse.addAll(u.usages(current.artifactType(), current.key())));
		if (!inUse.isEmpty()) {
			throw new ApiException(org.springframework.http.HttpStatus.valueOf(422), "FIELD_IN_USE",
				"O campo é citado por " + String.join(", ", inUse) + ". Tire a regra do gate antes de remover.");
		}
		jdbc.sql("delete from configuration.custom_field where id = :id").param("id", id).update();
		events.publish("custom_field.removed", user, orgUnits.rootId(),
			new Aggregate("CUSTOM_FIELD", id, current.label()), payload(current), snapshot(current), null);
	}

	// ---------------------------------------------------------------------------------------------- apoio

	private Optional<CustomField> find(UUID id) {
		return repository.customFields(null).stream().filter(f -> f.id().equals(id)).findFirst();
	}

	private ArtifactType type(String key) {
		return repository.type(key).orElseThrow(ApiException::notFound);
	}

	private UUID requireAdmin() {
		UUID user = currentUser.require().id();
		if (!authorization.can(user, Permission.MANAGE_CONFIGURATION, orgUnits.rootId())) {
			throw ApiException.forbidden();
		}
		return user;
	}

	private static Map<String, Object> payload(CustomField f) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("customFieldId", f.id().toString());
		payload.put("artifactType", f.artifactType());
		payload.put("key", f.key());
		return payload;
	}

	private static Map<String, Object> snapshot(CustomField f) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("label", f.label());
		snapshot.put("type", f.type());
		snapshot.put("options", f.options());
		snapshot.put("position", f.position());
		return snapshot;
	}

	private static String asText(Object value) {
		return value instanceof String s ? s.strip() : null;
	}

	private static String label(Object value) {
		String clean = asText(value);
		if (clean == null || clean.isEmpty() || clean.length() > LABEL_MAX) {
			throw ApiException.validation("label", "obrigatório, até " + LABEL_MAX + " caracteres");
		}
		return clean;
	}

	/** {@code SELECT} exige de 1 a 30 opções distintas; os demais tipos não têm opções. */
	private static List<String> options(String fieldType, Object value) {
		if (!"SELECT".equals(fieldType)) {
			if (value instanceof List<?> list && !list.isEmpty()) {
				throw ApiException.validation("options", "só o tipo SELECT tem opções");
			}
			return List.of();
		}
		if (!(value instanceof List<?> list)) {
			throw ApiException.validation("options", "informe as opções da lista");
		}
		Set<String> clean = new LinkedHashSet<>();
		for (Object item : list) {
			String option = asText(item);
			if (option == null || option.isEmpty() || option.length() > OPTION_MAX) {
				throw ApiException.validation("options", "cada opção é um texto de até " + OPTION_MAX + " caracteres");
			}
			if (!clean.add(option)) {
				throw ApiException.validation("options", "opção repetida: " + option);
			}
		}
		if (clean.isEmpty() || clean.size() > MAX_OPTIONS) {
			throw ApiException.validation("options", "de 1 a " + MAX_OPTIONS + " opções");
		}
		return List.copyOf(clean);
	}
}
