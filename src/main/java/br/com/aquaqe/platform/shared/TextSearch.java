package br.com.aquaqe.platform.shared;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Busca textual simples no PostgreSQL (ADR-11; #84), sem extensões: cada termo precisa aparecer no texto, sem diferença
 * de maiúsculas e acentos ("governanca" acha "Governança"). O Java dobra os termos com {@link #fold(String)} e o SQL
 * dobra o texto com {@link #foldSql(String)}; as duas formas tiram os mesmos acentos.
 */
public final class TextSearch {

	public static final int MIN_LENGTH = 2;
	public static final int MAX_LENGTH = 100;
	public static final int MAX_TERMS = 5;
	private static final int SNIPPET_BEFORE = 60;
	private static final int SNIPPET_LENGTH = 200;

	private static final String ACCENTED = "áàâãäåéèêëíìîïóòôõöúùûüçñ";
	private static final String PLAIN = "aaaaaaeeeeiiiiooooouuuucn";

	private TextSearch() {
	}

	/**
	 * Os termos da busca, já dobrados. {@code 400 VALIDATION_FAILED} com o campo {@code q} se a busca for vazia, curta
	 * demais (menos de {@value #MIN_LENGTH} caracteres) ou longa demais.
	 */
	public static List<String> terms(String q) {
		String query = q == null ? "" : q.strip();
		if (query.length() < MIN_LENGTH || query.length() > MAX_LENGTH) {
			throw ApiException.validation("q", "digite de " + MIN_LENGTH + " a " + MAX_LENGTH + " caracteres");
		}
		return Arrays.stream(fold(query).split("\\s+")).filter(t -> !t.isBlank()).distinct().limit(MAX_TERMS).toList();
	}

	/** Minúsculas e sem acentos. */
	public static String fold(String text) {
		if (text == null) {
			return "";
		}
		String lower = text.toLowerCase(Locale.ROOT);
		return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	/** A expressão SQL que dobra {@code expression} como {@link #fold(String)}. */
	public static String foldSql(String expression) {
		return "translate(lower(" + expression + "), '" + ACCENTED + "', '" + PLAIN + "')";
	}

	/**
	 * Condição SQL "todos os termos aparecem em {@code expression}" e os parâmetros dela ({@code prefix0},
	 * {@code prefix1}...). Os curingas {@code %} e {@code _} digitados contam como texto.
	 */
	public static Condition allTerms(String expression, List<String> terms, String prefix) {
		List<String> parts = new ArrayList<>();
		Map<String, Object> params = new LinkedHashMap<>();
		for (int i = 0; i < terms.size(); i++) {
			parts.add(foldSql(expression) + " like :" + prefix + i);
			params.put(prefix + i, "%" + escape(terms.get(i)) + "%");
		}
		return new Condition(parts.isEmpty() ? "true" : "(" + String.join(" and ", parts) + ")", params);
	}

	/**
	 * Trecho do texto em volta do primeiro termo encontrado, com reticências quando corta; o começo do texto se nenhum
	 * termo aparece (o termo pode estar só no título). Nulo para texto vazio.
	 */
	public static String snippet(String text, List<String> terms) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String clean = text.replaceAll("\\s+", " ").strip();
		String folded = fold(clean);
		int at = -1;
		for (String term : terms) {
			int found = folded.indexOf(term);
			if (found >= 0 && (at < 0 || found < at)) {
				at = found;
			}
		}
		// A dobra pode mudar o tamanho em casos raros; o índice serve só para posicionar o trecho.
		int start = at < 0 ? 0 : Math.max(0, Math.min(at, clean.length()) - SNIPPET_BEFORE);
		int end = Math.min(clean.length(), start + SNIPPET_LENGTH);
		if (start > 0) {
			int space = clean.indexOf(' ', start);
			start = space > 0 && space < at ? space + 1 : start;
		}
		return (start > 0 ? "…" : "") + clean.substring(start, end).strip() + (end < clean.length() ? "…" : "");
	}

	private static String escape(String term) {
		return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	/** Trecho de SQL e seus parâmetros nomeados. */
	public record Condition(String sql, Map<String, Object> params) {
	}
}
