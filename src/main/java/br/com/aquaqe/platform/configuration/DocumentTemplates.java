package br.com.aquaqe.platform.configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Templates de documento (SSD 8.4): as seções e o papel aprovador de cada tipo, usados pelo módulo {@code livingdocs}
 * para os documentos dos artefatos e para as páginas de conhecimento (Proposta 07). Vale a versão mais recente de cada
 * tipo; documentos existentes mantêm a versão com que foram criados (SSD 3.3).
 */
@Service
public class DocumentTemplates {

	private static final TypeReference<List<Map<String, Object>>> SECTIONS = new TypeReference<>() {
	};

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	DocumentTemplates(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Versão mais recente do template global do tipo. */
	public Optional<Template> latest(String documentType) {
		return jdbc.sql("""
				select id, document_type, version_number, name, sections::text as sections, approver_role, governed
				  from configuration.doc_template
				 where document_type = :type and org_unit_id is null
				 order by version_number desc limit 1
				""")
			.param("type", documentType)
			.query((rs, n) -> map(rs))
			.optional();
	}

	public Optional<Template> find(UUID id) {
		return jdbc.sql("""
				select id, document_type, version_number, name, sections::text as sections, approver_role, governed
				  from configuration.doc_template where id = :id
				""")
			.param("id", id)
			.query((rs, n) -> map(rs))
			.optional();
	}

	private Template map(java.sql.ResultSet rs) throws java.sql.SQLException {
		List<Section> sections = json.readValue(rs.getString("sections"), SECTIONS).stream()
			.map(s -> new Section((String) s.get("key"), (String) s.get("title"), Boolean.TRUE.equals(s.get("required"))))
			.toList();
		return new Template(rs.getObject("id", UUID.class), rs.getString("document_type"), rs.getInt("version_number"),
			rs.getString("name"), sections, rs.getString("approver_role"), rs.getBoolean("governed"));
	}

	/** {@code governed}: o documento passa por aprovação do {@code approverRole} antes de valer. */
	public record Template(UUID id, String documentType, int version, String name, List<Section> sections,
			String approverRole, boolean governed) {
	}

	public record Section(String key, String title, boolean required) {
	}
}
