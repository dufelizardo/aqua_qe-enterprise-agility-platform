package br.com.aquaqe.platform.livingdocs;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exportação para Markdown (#16; REV-11; SSD 6.4). Converte a versão que a pessoa lê (a mesma da leitura na tela) em
 * Markdown: título, metadados, uma seção {@code ##} por seção do template e os blocos, do editor contínuo
 * ({@code richtext}) e do editor anterior. Nos documentos do artefato, os anexos entram no fim como links. Marcas sem
 * equivalente em Markdown (sublinhado, cor e destaque) saem como texto simples.
 */
final class MarkdownExport {

	private MarkdownExport() {
	}

	/** Linha de metadados abaixo do título. */
	record Meta(String label, Object value) {
	}

	/** Anexo listado no fim do documento. */
	record AttachmentLink(String fileName, String url) {
	}

	static String document(String title, List<Meta> meta, List<?> sections, List<AttachmentLink> attachments) {
		StringBuilder out = new StringBuilder();
		out.append("# ").append(inline(title)).append("\n\n");
		for (Meta m : meta) {
			if (m.value() != null) {
				out.append("- **").append(m.label()).append(":** ").append(m.value()).append('\n');
			}
		}
		if (!meta.isEmpty()) {
			out.append('\n');
		}
		for (Object s : sections) {
			if (!(s instanceof Map<?, ?> section)) {
				continue;
			}
			out.append("## ").append(inline(String.valueOf(section.get("title")))).append("\n\n");
			int before = out.length();
			if (section.get("blocks") instanceof List<?> blocks) {
				for (Object block : blocks) {
					if (block instanceof Map<?, ?> map) {
						block(out, map);
					}
				}
			}
			if (out.length() == before) {
				out.append("_Seção não preenchida._\n\n");
			}
		}
		if (!attachments.isEmpty()) {
			out.append("## Anexos\n\n");
			for (AttachmentLink a : attachments) {
				out.append("- [").append(inline(a.fileName())).append("](").append(a.url()).append(")\n");
			}
			out.append('\n');
		}
		return out.toString().stripTrailing() + "\n";
	}

	/** Resposta para baixar: {@code text/markdown}, nome do arquivo a partir do título. */
	static ResponseEntity<String> download(String markdown, String fileBase) {
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(fileName(fileBase), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(markdown);
	}

	/** Nome do arquivo: o título sem acentos, só letras, números e hífen; nunca vazio. */
	static String fileName(String base) {
		String plain = Normalizer.normalize(base == null ? "" : base, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = plain.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
		if (slug.length() > 80) {
			slug = slug.substring(0, 80).replaceAll("-+$", "");
		}
		return (slug.isEmpty() ? "documento" : slug) + ".md";
	}

	// ------------------------------------------------------------------------------------------------ blocos

	private static void block(StringBuilder out, Map<?, ?> block) {
		String type = String.valueOf(block.get("type"));
		switch (type) {
			case "richtext" -> {
				if (block.get("doc") instanceof Map<?, ?> doc) {
					nodes(out, children(doc), "");
				}
			}
			case "paragraph" -> paragraph(out, text(block.get("text")));
			case "heading" -> {
				int level = block.get("level") instanceof Number n ? n.intValue() : 2;
				out.append("#".repeat(Math.max(3, Math.min(level + 1, 4)))).append(' ').append(text(block.get("text")))
					.append("\n\n");
			}
			case "list" -> {
				boolean ordered = Boolean.TRUE.equals(block.get("ordered"));
				int i = 1;
				if (block.get("items") instanceof List<?> items) {
					for (Object item : items) {
						out.append(ordered ? (i++) + ". " : "- ").append(text(item)).append('\n');
					}
					out.append('\n');
				}
			}
			case "table" -> {
				List<List<String>> rows = new ArrayList<>();
				if (block.get("header") instanceof List<?> header) {
					rows.add(header.stream().map(MarkdownExport::text).toList());
				}
				if (block.get("rows") instanceof List<?> list) {
					for (Object row : list) {
						if (row instanceof List<?> cells) {
							rows.add(cells.stream().map(MarkdownExport::text).toList());
						}
					}
				}
				table(out, rows);
			}
			case "code" -> code(out, text(block.get("language")), text(block.get("text")));
			case "callout", "quote" -> quote(out, text(block.get("text")));
			default -> {
				// Tipos desconhecidos não chegam a ser gravados (Blocks.validate).
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ richtext

	private static void nodes(StringBuilder out, List<Map<?, ?>> nodes, String indent) {
		for (Map<?, ?> node : nodes) {
			node(out, node, indent);
		}
	}

	private static void node(StringBuilder out, Map<?, ?> node, String indent) {
		String type = String.valueOf(node.get("type"));
		switch (type) {
			case "paragraph" -> {
				String text = inlineNodes(children(node));
				if (!text.isBlank()) {
					out.append(indent).append(text.replace("\n", "\n" + indent)).append("\n\n");
				}
			}
			case "heading" -> {
				int level = attrs(node).get("level") instanceof Number n ? n.intValue() : 2;
				// O título do documento é #, as seções são ##; os títulos de dentro da seção descem um nível.
				out.append("#".repeat(Math.max(3, Math.min(level + 1, 6)))).append(' ')
					.append(inlineNodes(children(node))).append("\n\n");
			}
			case "bulletList", "orderedList", "taskList" -> {
				list(out, node, type, indent);
				if (indent.isEmpty()) {
					out.append('\n');
				}
			}
			case "blockquote" -> {
				StringBuilder inner = new StringBuilder();
				nodes(inner, children(node), "");
				quote(out, inner.toString().strip());
			}
			case "codeBlock" -> code(out, text(attrs(node).get("language")), plainText(node));
			case "horizontalRule" -> out.append("---\n\n");
			case "table" -> {
				List<List<String>> rows = new ArrayList<>();
				for (Map<?, ?> row : children(node)) {
					rows.add(children(row).stream().map(cell -> {
						StringBuilder c = new StringBuilder();
						for (Map<?, ?> p : children(cell)) {
							if (!c.isEmpty()) {
								c.append("<br>");
							}
							c.append(inlineNodes(children(p)));
						}
						// O texto simples já sai com "|" escapado; falta o que vem de código em linha.
						return c.toString().replaceAll("(?<!\\\\)\\|", "\\\\|").replace("\n", "<br>");
					}).toList());
				}
				table(out, rows);
			}
			default -> {
				if (node.get("text") != null || type.equals("hardBreak")) {
					out.append(indent).append(inlineNodes(List.of(node))).append("\n\n");
				} else {
					nodes(out, children(node), indent);
				}
			}
		}
	}

	private static void list(StringBuilder out, Map<?, ?> list, String type, String indent) {
		int i = attrs(list).get("start") instanceof Number n ? n.intValue() : 1;
		for (Map<?, ?> item : children(list)) {
			String marker = switch (type) {
				case "orderedList" -> (i++) + ". ";
				case "taskList" -> Boolean.TRUE.equals(attrs(item).get("checked")) ? "- [x] " : "- [ ] ";
				default -> "- ";
			};
			String childIndent = indent + " ".repeat(marker.length());
			boolean first = true;
			for (Map<?, ?> child : children(item)) {
				String childType = String.valueOf(child.get("type"));
				if (first && "paragraph".equals(childType)) {
					out.append(indent).append(marker).append(inlineNodes(children(child)).replace("\n", "\n" + childIndent))
						.append('\n');
				} else if (List.of("bulletList", "orderedList", "taskList").contains(childType)) {
					if (first) {
						out.append(indent).append(marker).append('\n');
					}
					list(out, child, childType, childIndent);
				} else {
					if (first) {
						out.append(indent).append(marker).append('\n');
					}
					StringBuilder inner = new StringBuilder();
					node(inner, child, childIndent);
					out.append(inner.toString().stripTrailing()).append('\n');
				}
				first = false;
			}
			if (first) {
				out.append(indent).append(marker.stripTrailing()).append('\n');
			}
		}
	}

	private static String inlineNodes(List<Map<?, ?>> nodes) {
		StringBuilder out = new StringBuilder();
		for (Map<?, ?> node : nodes) {
			if ("hardBreak".equals(node.get("type"))) {
				out.append("  \n");
				continue;
			}
			if (node.get("text") == null) {
				out.append(inlineNodes(children(node)));
				continue;
			}
			String text = String.valueOf(node.get("text"));
			boolean isCode = false;
			String link = null;
			boolean bold = false;
			boolean italic = false;
			boolean strike = false;
			if (node.get("marks") instanceof List<?> marks) {
				for (Object m : marks) {
					if (m instanceof Map<?, ?> mark) {
						switch (String.valueOf(mark.get("type"))) {
							case "code" -> isCode = true;
							case "bold" -> bold = true;
							case "italic" -> italic = true;
							case "strike" -> strike = true;
							case "link" -> link = attrs(mark).get("href") instanceof String href ? href : null;
							default -> {
								// Sublinhado, cor e destaque não têm equivalente em Markdown.
							}
						}
					}
				}
			}
			String piece = isCode ? "`" + text.replace("`", "\\`") + "`" : escape(text);
			if (bold) {
				piece = "**" + piece + "**";
			}
			if (italic) {
				piece = "_" + piece + "_";
			}
			if (strike) {
				piece = "~~" + piece + "~~";
			}
			if (link != null) {
				piece = "[" + piece + "](" + link.replace(")", "%29").replace(" ", "%20") + ")";
			}
			out.append(piece);
		}
		return out.toString();
	}

	private static String plainText(Map<?, ?> node) {
		if (node.get("text") != null) {
			return String.valueOf(node.get("text"));
		}
		StringBuilder out = new StringBuilder();
		for (Map<?, ?> child : children(node)) {
			out.append(plainText(child));
		}
		return out.toString();
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private static void paragraph(StringBuilder out, String text) {
		if (!text.isBlank()) {
			out.append(text).append("\n\n");
		}
	}

	private static void quote(StringBuilder out, String text) {
		if (!text.isBlank()) {
			out.append("> ").append(text.replace("\n", "\n> ")).append("\n\n");
		}
	}

	private static void code(StringBuilder out, String language, String text) {
		String fence = text.contains("```") ? "~~~~" : "```";
		out.append(fence).append(language).append('\n').append(text.stripTrailing()).append('\n').append(fence)
			.append("\n\n");
	}

	private static void table(StringBuilder out, List<List<String>> rows) {
		if (rows.isEmpty()) {
			return;
		}
		int width = rows.stream().mapToInt(List::size).max().orElse(0);
		if (width == 0) {
			return;
		}
		for (int r = 0; r < rows.size(); r++) {
			List<String> row = rows.get(r);
			out.append('|');
			for (int c = 0; c < width; c++) {
				out.append(' ').append(c < row.size() ? row.get(c) : "").append(" |");
			}
			out.append('\n');
			if (r == 0) {
				out.append('|').append(" --- |".repeat(width)).append('\n');
			}
		}
		out.append('\n');
	}

	/** Escapa os caracteres que o Markdown leria como marcação no texto simples do editor. */
	private static String escape(String text) {
		return text.replaceAll("([\\\\`*_\\[\\]<>|])", "\\\\$1");
	}

	/** Título e textos curtos: escapados e numa linha só. */
	private static String inline(String text) {
		return escape(text == null ? "" : text).replace('\n', ' ');
	}

	/** Texto dos blocos do editor anterior: já está em Markdown em linha (SSD 6.2). */
	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static Map<?, ?> attrs(Map<?, ?> node) {
		return node.get("attrs") instanceof Map<?, ?> attrs ? attrs : Map.of();
	}

	private static List<Map<?, ?>> children(Map<?, ?> node) {
		List<Map<?, ?>> result = new ArrayList<>();
		if (node.get("content") instanceof List<?> content) {
			for (Object child : content) {
				if (child instanceof Map<?, ?> map) {
					result.add(map);
				}
			}
		}
		return result;
	}
}

/**
 * {@code GET .../export/markdown} (#16): baixa o documento do artefato ou a página do Wiki (e o Regimento) em Markdown,
 * com a mesma permissão e a mesma versão da leitura.
 */
@RestController
class ExportController {

	private final ArtifactDocumentService documents;
	private final PageService pages;
	private final AttachmentService attachments;

	ExportController(ArtifactDocumentService documents, PageService pages, AttachmentService attachments) {
		this.documents = documents;
		this.pages = pages;
		this.attachments = attachments;
	}

	@GetMapping("/api/v1/artifacts/{artifact}/documents/{type}/export/markdown")
	ResponseEntity<String> artifactDocument(@PathVariable String artifact, @PathVariable String type) {
		Map<String, Object> doc = documents.get(artifact, type);
		String key = String.valueOf(doc.get("artifactKey"));
		List<MarkdownExport.AttachmentLink> links = new ArrayList<>();
		if (attachments.list(artifact).get("items") instanceof List<?> items) {
			for (Object item : items) {
				if (item instanceof Map<?, ?> a) {
					links.add(new MarkdownExport.AttachmentLink(String.valueOf(a.get("fileName")),
						"/api/v1/attachments/" + a.get("id") + "/content"));
				}
			}
		}
		String title = doc.get("name") + " — " + key;
		String markdown = MarkdownExport.document(title, List.of(
				new MarkdownExport.Meta("Status", doc.get("status")),
				new MarkdownExport.Meta("Versão", doc.get("versionNumber")),
				new MarkdownExport.Meta("Autor", author(doc)),
				new MarkdownExport.Meta("Salvo em", doc.get("savedAt"))),
			(List<?>) doc.get("sections"), links);
		return MarkdownExport.download(markdown, key + "-" + type);
	}

	@GetMapping("/api/v1/pages/{id}/export/markdown")
	ResponseEntity<String> page(@PathVariable UUID id) {
		Map<String, Object> page = pages.get(id);
		String title = String.valueOf(page.get("title"));
		String markdown = MarkdownExport.document(title, List.of(
				new MarkdownExport.Meta("Status", page.get("status")),
				new MarkdownExport.Meta("Versão", page.get("versionNumber")),
				new MarkdownExport.Meta("Autor", author(page)),
				new MarkdownExport.Meta("Salvo em", page.get("savedAt"))),
			(List<?>) page.get("sections"), List.of());
		return MarkdownExport.download(markdown, title);
	}

	private static Object author(Map<String, Object> view) {
		return view.get("author") instanceof Map<?, ?> author ? author.get("name") : null;
	}
}
