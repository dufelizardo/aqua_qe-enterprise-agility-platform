package br.com.aquaqe.platform.livingdocs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Conversão de documento para Markdown (#16; REV-11; SSD 6.4), sem banco. */
class MarkdownExportTests {

	private static Map<String, Object> text(String text, String... marks) {
		return Map.of("type", "text", "text", text, "marks",
			List.of(marks).stream().map(m -> Map.of("type", m)).toList());
	}

	private static Map<String, Object> node(String type, Object... content) {
		return Map.of("type", type, "content", List.of(content));
	}

	@Test
	void richtextViraMarkdown() {
		Map<String, Object> doc = node("doc",
			Map.of("type", "heading", "attrs", Map.of("level", 2), "content", List.of(text("Objetivos"))),
			node("paragraph", text("Crescer", "bold"), text(" com "), text("foco", "italic"), text(" e "),
				Map.of("type", "text", "text", "site", "marks",
					List.of(Map.of("type", "link", "attrs", Map.of("href", "https://exemplo.com"))))),
			node("bulletList", node("listItem", node("paragraph", text("Um"))),
				node("listItem", node("paragraph", text("Dois")),
					node("bulletList", node("listItem", node("paragraph", text("Dois A")))))),
			node("orderedList", node("listItem", node("paragraph", text("Primeiro")))),
			node("taskList", Map.of("type", "taskItem", "attrs", Map.of("checked", true), "content",
				List.of(node("paragraph", text("Feito"))))),
			node("blockquote", node("paragraph", text("Citação"))),
			Map.of("type", "codeBlock", "attrs", Map.of("language", "json"), "content", List.of(text("{\"a\":1}"))),
			Map.of("type", "horizontalRule"),
			node("table",
				node("tableRow", node("tableHeader", node("paragraph", text("A"))),
					node("tableHeader", node("paragraph", text("B|C")))),
				node("tableRow", node("tableCell", node("paragraph", text("1"))),
					node("tableCell", node("paragraph", text("2"))))),
			node("paragraph", text("use `x` e *y*", "code")));
		List<?> sections = List.of(
			Map.of("title", "Visão geral", "blocks", List.of(Map.of("type", "richtext", "doc", doc))),
			Map.of("title", "Riscos", "blocks", List.of(
				Map.of("type", "paragraph", "text", "Texto **antigo**"),
				Map.of("type", "list", "ordered", false, "items", List.of("x", "y")))),
			Map.of("title", "Vazia", "blocks", List.of()));

		String md = MarkdownExport.document("PRD — INIT-1",
			List.of(new MarkdownExport.Meta("Status", "DRAFT"), new MarkdownExport.Meta("Autor", null)), sections,
			List.of(new MarkdownExport.AttachmentLink("plano.pdf", "/api/v1/attachments/1/content")));

		assertTrue(md.startsWith("# PRD — INIT-1\n\n- **Status:** DRAFT\n\n## Visão geral\n\n### Objetivos\n\n"), md);
		assertTrue(md.contains("**Crescer** com _foco_ e [site](https://exemplo.com)\n"), md);
		assertTrue(md.contains("- Um\n- Dois\n  - Dois A\n"), md);
		assertTrue(md.contains("1. Primeiro\n"), md);
		assertTrue(md.contains("- [x] Feito\n"), md);
		assertTrue(md.contains("> Citação\n"), md);
		assertTrue(md.contains("```json\n{\"a\":1}\n```\n"), md);
		assertTrue(md.contains("\n---\n"), md);
		assertTrue(md.contains("| A | B\\|C |\n| --- | --- |\n| 1 | 2 |\n"), md);
		assertTrue(md.contains("`use \\`x\\` e *y*`"), md);
		assertTrue(md.contains("## Riscos\n\nTexto **antigo**\n\n- x\n- y\n"), md);
		assertTrue(md.contains("## Vazia\n\n_Seção não preenchida._\n"), md);
		assertTrue(md.endsWith("## Anexos\n\n- [plano.pdf](/api/v1/attachments/1/content)\n"), md);
		assertFalse(md.contains("Autor"), md);
	}

	@Test
	void textoSimplesEscapaAMarcacao() {
		String md = MarkdownExport.document("Título *com* marca", List.of(),
			List.of(Map.of("title", "S", "blocks", List.of(Map.of("type", "richtext", "doc",
				node("doc", node("paragraph", text("2 * 3 = _seis_"))))))),
			List.of());
		assertTrue(md.startsWith("# Título \\*com\\* marca\n"), md);
		assertTrue(md.contains("2 \\* 3 = \\_seis\\_"), md);
	}

	@Test
	void nomeDoArquivo() {
		assertEquals("INIT-12-PRD.md", MarkdownExport.fileName("INIT-12-PRD"));
		assertEquals("Guia-de-Integracao-v2.md", MarkdownExport.fileName("Guia de Integração (v2)!"));
		assertEquals("documento.md", MarkdownExport.fileName("???"));
	}
}
