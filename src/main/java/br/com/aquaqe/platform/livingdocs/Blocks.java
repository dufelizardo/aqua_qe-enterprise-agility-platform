package br.com.aquaqe.platform.livingdocs;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.aquaqe.platform.shared.ApiException;

/**
 * Blocos do conteúdo (SSD 6; REV-05): o contrato é independente do editor. Tipos aceitos nesta entrega: parágrafo e
 * destaque com Markdown leve, título, lista, tabela, citação e código. Imagens e anexos entram com o armazenamento de
 * objetos (ADR-10).
 */
final class Blocks {

	static final Set<String> TYPES = Set.of("paragraph", "heading", "list", "table", "code", "callout", "quote");
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
		}
	}

	/** Uma seção está preenchida quando algum bloco tem texto (critério de aprovação, SSD 6.3). */
	static boolean filled(List<?> blocks) {
		return blocks != null && blocks.stream().anyMatch(Blocks::hasText);
	}

	private static boolean hasText(Object value) {
		return switch (value) {
			case String text -> !text.isBlank();
			case Map<?, ?> map -> map.entrySet().stream()
				.filter(e -> !"type".equals(e.getKey()) && !"id".equals(e.getKey()) && !"level".equals(e.getKey())
					&& !"language".equals(e.getKey()) && !"tone".equals(e.getKey()))
				.anyMatch(e -> hasText(e.getValue()));
			case Collection<?> list -> list.stream().anyMatch(Blocks::hasText);
			case null, default -> false;
		};
	}
}
