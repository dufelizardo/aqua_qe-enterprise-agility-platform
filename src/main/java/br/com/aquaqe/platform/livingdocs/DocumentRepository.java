package br.com.aquaqe.platform.livingdocs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistência do motor de documentos (SAD 7.6): documento com dono genérico e versões imutáveis. Nesta entrega, só as
 * páginas de conhecimento (dono = unidade organizacional) usam o repositório; os documentos dos artefatos vêm a seguir.
 */
@Repository
class DocumentRepository {

	private static final String COLUMNS = """
			id, org_unit_id, parent_id, type, title, position, status, template_id, current_version, approved_version,
			published_version, archived_at, archive_reason, created_by, created_at, updated_at
			""";

	private final JdbcClient jdbc;

	DocumentRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<PageRow> pagesOf(UUID orgUnitId) {
		return jdbc.sql("select " + COLUMNS + """
				  from livingdocs.document
				 where owner_type = 'ORG_UNIT' and org_unit_id = :unit
				 order by position, created_at
				""")
			.param("unit", orgUnitId)
			.query((rs, n) -> page(rs))
			.list();
	}

	Optional<PageRow> page(UUID id) {
		return jdbc.sql("select " + COLUMNS + " from livingdocs.document where owner_type = 'ORG_UNIT' and id = :id")
			.param("id", id)
			.query((rs, n) -> page(rs))
			.optional();
	}

	UUID insertPage(UUID orgUnitId, UUID parentId, String type, String title, String status, UUID templateId,
			Integer publishedVersion, UUID createdBy) {
		return jdbc.sql("""
				insert into livingdocs.document (owner_type, org_unit_id, parent_id, type, title, status, template_id,
				                                 current_version, published_version, created_by)
				values ('ORG_UNIT', :unit, :parent, :type, :title, :status, :template, 1, :published, :by)
				returning id
				""")
			.param("unit", orgUnitId)
			.param("parent", parentId)
			.param("type", type)
			.param("title", title)
			.param("status", status)
			.param("template", templateId)
			.param("published", publishedVersion)
			.param("by", createdBy)
			.query(UUID.class)
			.single();
	}

	void insertVersion(UUID documentId, int number, String sectionsJson, String summary, UUID authorId) {
		jdbc.sql("""
				insert into livingdocs.document_version (document_id, number, sections, summary, author_id)
				values (:doc, :number, cast(:sections as jsonb), :summary, :author)
				""")
			.param("doc", documentId)
			.param("number", number)
			.param("sections", sectionsJson)
			.param("summary", summary)
			.param("author", authorId)
			.update();
	}

	/** Grava a nova versão corrente só se ninguém gravou antes (bloqueio otimista, SAD 7.6). */
	boolean advance(UUID id, int baseVersion, String status, Integer publishedVersion) {
		return jdbc.sql("""
				update livingdocs.document
				   set current_version = current_version + 1, status = :status, published_version = :published,
				       updated_at = now()
				 where id = :id and current_version = :base
				""")
			.param("id", id)
			.param("base", baseVersion)
			.param("status", status)
			.param("published", publishedVersion)
			.update() == 1;
	}

	void changeStatus(UUID id, String status, Integer approvedVersion, Integer publishedVersion) {
		jdbc.sql("""
				update livingdocs.document
				   set status = :status, approved_version = :approved, published_version = :published, updated_at = now()
				 where id = :id
				""")
			.param("id", id)
			.param("status", status)
			.param("approved", approvedVersion)
			.param("published", publishedVersion)
			.update();
	}

	void changeMeta(UUID id, String title, UUID parentId, boolean moved) {
		jdbc.sql("""
				update livingdocs.document
				   set title = :title, parent_id = :parent, updated_at = now(),
				       position = case when :moved then extract(epoch from clock_timestamp()) * 1000 else position end
				 where id = :id
				""")
			.param("id", id)
			.param("title", title)
			.param("parent", parentId)
			.param("moved", moved)
			.update();
	}

	/** Nova posição (e, ao mudar de nível, novo pai) sem criar versão nem mexer no status (PROP-002). */
	void reposition(UUID id, UUID parentId, double position) {
		jdbc.sql("update livingdocs.document set parent_id = :parent, position = :position, updated_at = now() where id = :id")
			.param("id", id)
			.param("parent", parentId)
			.param("position", position)
			.update();
	}

	void archive(UUID id, String reason) {
		jdbc.sql("update livingdocs.document set archived_at = now(), archive_reason = :reason, updated_at = now() where id = :id")
			.param("id", id)
			.param("reason", reason)
			.update();
	}

	void restore(UUID id, UUID parentId) {
		jdbc.sql("""
				update livingdocs.document
				   set archived_at = null, archive_reason = null, parent_id = :parent, updated_at = now()
				 where id = :id
				""")
			.param("id", id)
			.param("parent", parentId)
			.update();
	}

	boolean hasActiveChildren(UUID id) {
		return jdbc.sql("select exists (select 1 from livingdocs.document where parent_id = :id and archived_at is null)")
			.param("id", id)
			.query(Boolean.class)
			.single();
	}

	List<VersionRow> versions(UUID documentId) {
		return jdbc.sql("""
				select number, null as sections, summary, author_id, saved_at from livingdocs.document_version
				 where document_id = :doc order by number desc
				""")
			.param("doc", documentId)
			.query((rs, n) -> version(rs))
			.list();
	}

	Optional<VersionRow> version(UUID documentId, int number) {
		return jdbc.sql("""
				select number, sections::text as sections, summary, author_id, saved_at from livingdocs.document_version
				 where document_id = :doc and number = :number
				""")
			.param("doc", documentId)
			.param("number", number)
			.query((rs, n) -> version(rs))
			.optional();
	}

	private static PageRow page(ResultSet rs) throws SQLException {
		return new PageRow(rs.getObject("id", UUID.class), rs.getObject("org_unit_id", UUID.class),
			rs.getObject("parent_id", UUID.class), rs.getString("type"), rs.getString("title"), rs.getDouble("position"),
			rs.getString("status"), rs.getObject("template_id", UUID.class), rs.getInt("current_version"),
			(Integer) rs.getObject("approved_version"), (Integer) rs.getObject("published_version"),
			instant(rs.getTimestamp("archived_at")), rs.getString("archive_reason"), rs.getObject("created_by", UUID.class),
			instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
	}

	private static VersionRow version(ResultSet rs) throws SQLException {
		return new VersionRow(rs.getInt("number"), rs.getString("sections"), rs.getString("summary"),
			rs.getObject("author_id", UUID.class), instant(rs.getTimestamp("saved_at")));
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	record PageRow(UUID id, UUID orgUnitId, UUID parentId, String type, String title, double position, String status,
			UUID templateId, int currentVersion, Integer approvedVersion, Integer publishedVersion, Instant archivedAt,
			String archiveReason, UUID createdBy, Instant createdAt, Instant updatedAt) {

		boolean archived() {
			return archivedAt != null;
		}
	}

	record VersionRow(int number, String sectionsJson, String summary, UUID authorId, Instant savedAt) {
	}
}
