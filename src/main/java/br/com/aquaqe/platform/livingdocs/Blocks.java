package br.com.aquaqe.platform.livingdocs;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.aquaqe.platform.shared.ApiException;

/**
 * Blocos do conteúdo (SSD 6). Desde o bug #67 o editor é de texto contínuo (Tiptap/ProseMirror) e grava cada seção como
 * um bloco {@code richtext}, com o documento do editor em {@code doc}. Os tipos do editor anterior (parágrafo, título,
 * lista, tabela, código, destaque, citação) continuam aceitos, para o conteúdo já gravado. Imagens e anexos entram com o
 * armazenamento de objetos (ADR-10).
 */
final class Blocks {

	static final Set<String> TYPES = Set.of("richtext", "paragraph", "heading", "list", "table", "code", "callout",
		"quote");
	/** Limite de uma versão, sem contar anexos (SSD 6). */
	static final int MAX_BYTES = 1024 * 1024;

	private Blocks() {
	}

	/** Recusa blocos sem tipo conhecido. */
	static void validate(String sectionKey, List<?> blocks) {
		for (Object block : blocks) {
			if (!(block instanceof Map<?, ?> map) || !(map.get("type") instanceof String type) || !TYPES.contains(type)) {
				throw ApiException.validation("sections." + sectionKey, "bloco de tipo desconhecido");
			}
			if ("richtext".equals(type) && !(map.get("doc") instanceof Map<?, ?> doc && "doc".equals(doc.get("type")))) {
				throw ApiException.validation("sections." + sectionKey, "richtext precisa do documento do editor em doc");
			}
			if ("richtext".equals(type) && !colorsValid(map.get("doc"))) {
				throw ApiException.validation("sections." + sectionKey, "cor do texto ou do destaque inválida");
			}
		}
	}

	/**
	 * Cor do texto e do destaque (#86): só {@code #rrggbb}. O editor grava a cor no atributo {@code color} das marcas
	 * {@code textStyle} e {@code highlight}; qualquer outro valor poderia levar CSS para dentro da página.
	 */
	static boolean colorsValid(Object value) {
		return switch (value) {
			case Map<?, ?> map -> {
				if (map.get("attrs") instanceof Map<?, ?> attrs && attrs.get("color") != null
						&& !(attrs.get("color") instanceof String color && COLOR.matcher(color).matches())) {
					yield false;
				}
				yield map.values().stream().allMatch(Blocks::colorsValid);
			}
			case Collection<?> list -> list.stream().allMatch(Blocks::colorsValid);
			case null, default -> true;
		};
	}

	private static final java.util.regex.Pattern COLOR = java.util.regex.Pattern.compile("#[0-9a-fA-F]{6}");

	/** Uma seção está preenchida quando algum bloco tem texto (critério de aprovação, SSD 6.3). */
	static boolean filled(List<?> blocks) {
		return blocks != null && blocks.stream().anyMatch(Blocks::hasText);
	}

	private static boolean hasText(Object value) {
		return switch (value) {
			case String text -> !text.isBlank();
			case Map<?, ?> map -> map.entrySet().stream()
				.filter(e -> !"type".equals(e.getKey()) && !"id".equals(e.getKey()) && !"level".equals(e.getKey())
					&& !"language".equals(e.getKey()) && !"tone".equals(e.getKey()) && !"color".equals(e.getKey())
					&& !"checked".equals(e.getKey()))
				.anyMatch(e -> hasText(e.getValue()));
			case Collection<?> list -> list.stream().anyMatch(Blocks::hasText);
			case null, default -> false;
		};
	}
}
