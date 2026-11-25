package br.com.aquaqe.platform.livingdocs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.configuration.DocumentTemplates.Section;
import br.com.aquaqe.platform.configuration.DocumentTemplates.Template;
import br.com.aquaqe.platform.shared.ApiException;
import tools.jackson.databind.ObjectMapper;

/**
 * Seções de um documento conforme o template (SSD 6): validação do corpo, leitura de uma versão gravada e seções
 * obrigatórias vazias. Comum às páginas de conhecimento e aos documentos dos artefatos.
 */
@Component
class DocumentSections {

	private final ObjectMapper json;

	DocumentSections(ObjectMapper json) {
		this.json = json;
	}

	/** Seções do corpo na ordem do template; não se acrescentam, removem nem renomeiam seções (SSD 6.1). */
	List<Map<String, Object>> fromBody(Template template, Object value) {
		if (!(value instanceof List<?> list)) {
			throw ApiException.validation("sections", "obrigatório");
		}
		Map<String, List<?>> byKey = new HashMap<>();
		for (Object item : list) {
			if (!(item instanceof Map<?, ?> map) || !(map.get("key") instanceof String key)) {
				throw ApiException.validation("sections", "cada seção precisa de key");
			}
			if (!(map.get("blocks") instanceof List<?> blocks)) {
				throw ApiException.validation("sections." + key, "blocks é obrigatório");
			}
			Blocks.validate(key, blocks);
			byKey.put(key, blocks);
		}
		Set<String> expected = new HashSet<>(template.sections().stream().map(Section::key).toList());
		if (!expected.equals(byKey.keySet())) {
			throw ApiException.validation("sections", "as seções devem ser exatamente as do template");
		}
		return template.sections().stream()
			.map(s -> Map.<String, Object>of("key", s.key(), "blocks", byKey.get(s.key())))
			.toList();
	}

	/** Blocos de cada seção de uma versão gravada ({@code null}: documento ainda sem versão). */
	@SuppressWarnings("unchecked")
	Map<String, List<?>> blocksByKey(String sectionsJson) {
		Map<String, List<?>> result = new HashMap<>();
		if (sectionsJson == null) {
			return result;
		}
		for (Map<String, Object> section : (List<Map<String, Object>>) json.readValue(sectionsJson, List.class)) {
			result.put(String.valueOf(section.get("key")), (List<?>) section.get("blocks"));
		}
		return result;
	}

	/** Chaves das seções obrigatórias vazias (critério da seção 6.3 do SSD). */
	List<String> missing(Template template, String sectionsJson) {
		Map<String, List<?>> blocks = blocksByKey(sectionsJson);
		return template.sections().stream()
			.filter(Section::required)
			.filter(s -> !Blocks.filled(blocks.get(s.key())))
			.map(Section::key)
			.toList();
	}

	String toJson(Object value) {
		return json.writeValueAsString(value);
	}
}
