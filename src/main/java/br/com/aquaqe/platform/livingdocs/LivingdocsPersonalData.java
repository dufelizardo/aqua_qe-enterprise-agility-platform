package br.com.aquaqe.platform.livingdocs;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.shared.PersonalData;

/**
 * Comentários e anexos da pessoa (#32; REV-14). São conteúdo da página e do artefato, não da pessoa: a anonimização não
 * os apaga, e a autoria passa a aparecer com o pseudônimo da conta. Versões de documentos guardam só o id do autor.
 */
@Component
class LivingdocsPersonalData implements PersonalData {

	private final JdbcClient jdbc;

	LivingdocsPersonalData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "livingdocs";
	}

	@Override
	public Object export(UUID userId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("comments", jdbc.sql("""
				select id, document_id, body, quote, created_at, deleted_at
				  from livingdocs.page_comment where author_id = :id order by created_at
				""")
			.param("id", userId)
			.query((rs, n) -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id", rs.getObject("id", UUID.class));
				item.put("pageId", rs.getObject("document_id", UUID.class));
				item.put("body", rs.getObject("deleted_at") == null ? rs.getString("body") : null);
				item.put("quote", rs.getString("quote"));
				item.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
				item.put("deleted", rs.getObject("deleted_at") != null);
				return item;
			})
			.list());
		result.put("attachments", jdbc.sql("""
				select id, artifact_id, file_name, size_bytes, uploaded_at, removed_at
				  from livingdocs.attachment where uploaded_by = :id order by uploaded_at
				""")
			.param("id", userId)
			.query((rs, n) -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("id", rs.getObject("id", UUID.class));
				item.put("artifactId", rs.getObject("artifact_id", UUID.class));
				item.put("fileName", rs.getString("file_name"));
				item.put("size", rs.getLong("size_bytes"));
				item.put("uploadedAt", rs.getObject("uploaded_at", OffsetDateTime.class));
				item.put("removed", rs.getObject("removed_at") != null);
				return item;
			})
			.list());
		return result;
	}

	@Override
	public void anonymize(UUID userId) {
		// Conteúdo da página e do artefato: fica, com a autoria pseudonimizada pela conta.
	}
}
