package br.com.aquaqe.platform.workitem;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.CustomField;
import br.com.aquaqe.platform.shared.ApiException;

/**
 * Valores dos campos customizados (#17; REV-12). Só os campos declarados no tipo são aceitos, com o tipo certo. A
 * gravação segue JSON Merge Patch (SSD 1.5): a chave enviada muda, {@code null} (ou texto vazio) limpa, e as ausentes
 * ficam como estão, inclusive valores de campos que deixaram de ser declarados.
 */
final class CustomFieldValues {

	static final int TEXT_MAX = 2000;

	private CustomFieldValues() {
	}

	static Map<String, Object> merge(String typeName, List<CustomField> declared, Map<String, Object> current,
			Object patch) {
		Map<String, Object> result = new LinkedHashMap<>(current == null ? Map.of() : current);
		if (patch == null) {
			return result;
		}
		if (!(patch instanceof Map<?, ?> changes)) {
			throw ApiException.validation("customFields", "deve ser um objeto");
		}
		for (Map.Entry<?, ?> change : changes.entrySet()) {
			String key = String.valueOf(change.getKey());
			String field = "customFields." + key;
			CustomField definition = declared.stream().filter(f -> f.key().equals(key)).findFirst()
				.orElseThrow(() -> ApiException.validation(field, "campo não declarado em " + typeName));
			Optional<Object> value = value(definition, change.getValue(), field);
			if (value.isPresent()) {
				result.put(key, value.get());
			} else {
				result.remove(key);
			}
		}
		return result;
	}

	private static Optional<Object> value(CustomField f, Object value, String field) {
		if (value == null || value instanceof String s && s.isBlank()) {
			return Optional.empty();
		}
		switch (f.type()) {
			case "TEXT" -> {
				if (!(value instanceof String s) || s.strip().length() > TEXT_MAX) {
					throw ApiException.validation(field, "texto de até " + TEXT_MAX + " caracteres");
				}
				return Optional.of(s.strip());
			}
			case "NUMBER" -> {
				if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
					throw ApiException.validation(field, "deve ser um número");
				}
				return Optional.of(n);
			}
			case "DATE" -> {
				try {
					return Optional.of(LocalDate.parse(String.valueOf(value)).toString());
				} catch (DateTimeParseException e) {
					throw ApiException.validation(field, "data no formato AAAA-MM-DD");
				}
			}
			case "BOOLEAN" -> {
				if (!(value instanceof Boolean b)) {
					throw ApiException.validation(field, "deve ser true ou false");
				}
				return Optional.of(b);
			}
			case "SELECT" -> {
				if (!(value instanceof String s) || !f.options().contains(s)) {
					throw ApiException.validation(field, "use uma das opções: " + String.join(", ", f.options()));
				}
				return Optional.of(s);
			}
			default -> throw new IllegalStateException("Tipo de campo desconhecido: " + f.type());
		}
	}
}
