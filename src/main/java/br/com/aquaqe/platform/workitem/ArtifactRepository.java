package br.com.aquaqe.platform.workitem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso às tabelas do schema {@code workitem}. Toda consulta de leitura recebe o escopo do usuário (unidades em que
 * ele lê tudo) e aplica as regras 1, 2 e 6 do SSD 2.1 no próprio SQL: um artefato é legível quando ele, ou um
 * ancestral dele, está numa unidade do escopo.
 */
@Repository
class ArtifactRepository {

	private static final String COLUMNS = """
		a.id, a.key, a.type_id, a.parent_id, a.path, a.org_unit_id, a.status_id, a.title, a.description,
		a.owner_id, a.technical_lead_id, a.qa_owner_id, a.assignee_id, a.size, cast(a.custom_fields as text) as custom_fields,
		a.archived, a.version, a.created_at, a.updated_at, a.blocked, a.blocked_reason,
		(select count(*) from workitem.artifact c where c.parent_id = a.id and not c.archived) as child_count
		""";

	/** O artefato {@code a}, ou um ancestral dele, está numa unidade do escopo. */
	private static final String READABLE = """
		exists (select 1 from workitem.artifact x
		         where x.org_unit_id = any(cast(:scope as uuid[])) and a.path like x.path || '%')
		""";

	private final JdbcClient jdbc;

	ArtifactRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Row> find(UUID id) {
		return jdbc.sql("select " + COLUMNS + " from workitem.artifact a where a.id = :id")
			.param("id", id)
			.query((rs, n) -> row(rs))
			.optional();
	}

	Optional<Row> findByKey(String key) {
		return jdbc.sql("select " + COLUMNS + " from workitem.artifact a where a.key = :key")
			.param("key", key)
			.query((rs, n) -> row(rs))
			.optional();
	}

	boolean readable(UUID artifactId, Collection<UUID> scope) {
		return jdbc.sql("select exists (select 1 from workitem.artifact a where a.id = :id and " + READABLE + ")")
			.param("id", artifactId)
			.param("scope", array(scope))
			.query(Boolean.class)
			.single();
	}

	/** Linhas pedidas, na ordem dos ids (ancestrais para o contexto herdado). */
	List<Row> rows(List<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<UUID, Row> found = new HashMap<>();
		jdbc.sql("select " + COLUMNS + " from workitem.artifact a where a.id in (:ids)")
			.param("ids", ids)
			.query((rs, n) -> row(rs))
			.list()
			.forEach(r -> found.put(r.id(), r));
		return ids.stream().map(found::get).filter(r -> r != null).toList();
	}

	List<Row> children(UUID parentId, boolean archived) {
		return jdbc.sql("select " + COLUMNS
				+ " from workitem.artifact a where a.parent_id = :parent and a.archived = :archived order by a.created_at, a.key")
			.param("parent", parentId)
			.param("archived", archived)
			.query((rs, n) -> row(rs))
			.list();
	}

	List<Row> search(Filter filter, Collection<UUID> scope, long offset, int limit) {
		StringBuilder sql = new StringBuilder("select " + COLUMNS + " from workitem.artifact a where ");
		Map<String, Object> params = where(sql, filter, scope);
		sql.append(filter.byRank() ? " order by a.rank, a.created_at, a.key" : " order by a.created_at desc, a.key");
		sql.append(" offset :offset limit :limit");
		JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).params(params);
		return spec.param("offset", offset).param("limit", limit).query((rs, n) -> row(rs)).list();
	}

	/**
	 * Busca textual (#84): artefatos não arquivados que o usuário lê, com todos os termos na chave, no título ou na
	 * descrição; primeiro os que têm os termos na chave ou no título, depois os mais recentes.
	 */
	List<Row> textSearch(List<String> terms, Collection<UUID> scope, int limit) {
		br.com.aquaqe.platform.shared.TextSearch.Condition all = br.com.aquaqe.platform.shared.TextSearch
			.allTerms("a.key || ' ' || a.title || ' ' || coalesce(a.description, '')", terms, "t");
		br.com.aquaqe.platform.shared.TextSearch.Condition heading = br.com.aquaqe.platform.shared.TextSearch
			.allTerms("a.key || ' ' || a.title", terms, "t");
		return jdbc.sql("select " + COLUMNS + " from workitem.artifact a where " + READABLE + " and not a.archived and "
				+ all.sql() + " order by " + heading.sql() + " desc, a.updated_at desc, a.key limit :limit")
			.params(all.params())
			.param("scope", array(scope))
			.param("limit", limit)
			.query((rs, n) -> row(rs))
			.list();
	}

	/** Dos artefatos informados, os não arquivados que o usuário lê. */
	List<Row> readableAmong(Collection<UUID> ids, Collection<UUID> scope) {
		if (ids.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("select " + COLUMNS + " from workitem.artifact a where a.id = any(cast(:ids as uuid[])) and "
				+ READABLE + " and not a.archived")
			.param("ids", array(ids))
			.param("scope", array(scope))
			.query((rs, n) -> row(rs))
			.list();
	}

	long count(Filter filter, Collection<UUID> scope) {
		StringBuilder sql = new StringBuilder("select count(*) from workitem.artifact a where ");
		Map<String, Object> params = where(sql, filter, scope);
		return jdbc.sql(sql.toString()).params(params).query(Long.class).single();
	}

	private static Map<String, Object> where(StringBuilder sql, Filter f, Collection<UUID> scope) {
		Map<String, Object> params = new HashMap<>();
		List<String> conditions = new ArrayList<>();
		// Sem escopo: contagem da coluna inteira para o limite de WIP, que vale para todos os itens.
		if (scope != null) {
			conditions.add(READABLE);
			params.put("scope", array(scope));
		}
		// Workspace: o artefato, ou um ancestral dele, está na subárvore da unidade escolhida (SSD 1.2 e 2.1).
		conditions.add("""
			exists (select 1 from workitem.artifact w
			         where w.org_unit_id = any(cast(:workspace as uuid[])) and a.path like w.path || '%')
			""");
		params.put("workspace", array(f.workspaceUnits()));
		conditions.add("a.archived = :archived");
		params.put("archived", f.archived());
		if (f.typeIds() != null) {
			conditions.add("a.type_id = any(cast(:types as uuid[]))");
			params.put("types", array(f.typeIds()));
		}
		if (f.statusIds() != null) {
			conditions.add("a.status_id = any(cast(:statuses as uuid[]))");
			params.put("statuses", array(f.statusIds()));
		}
		optional(conditions, params, "a.org_unit_id = :orgUnit", "orgUnit", f.orgUnitId());
		optional(conditions, params, "a.parent_id = :parent", "parent", f.parentId());
		optional(conditions, params, "a.owner_id = :owner", "owner", f.ownerId());
		optional(conditions, params, "a.assignee_id = :assignee", "assignee", f.assigneeId());
		if (f.q() != null && !f.q().isBlank()) {
			conditions.add("(a.title ilike :q or a.key ilike :q)");
			params.put("q", "%" + f.q().strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
		}
		sql.append(String.join(" and ", conditions));
		return params;
	}

	private static void optional(List<String> conditions, Map<String, Object> params, String condition, String name,
			Object value) {
		if (value != null) {
			conditions.add(condition);
			params.put(name, value);
		}
	}

	java.math.BigDecimal rankOf(UUID id) {
		return jdbc.sql("select rank from workitem.artifact where id = :id").param("id", id)
			.query(java.math.BigDecimal.class).single();
	}

	/** Posição vizinha a {@code rank}, ignorando o próprio item: a maior abaixo ({@code before}) ou a menor acima. */
	java.util.Optional<java.math.BigDecimal> neighborRank(java.math.BigDecimal rank, UUID ignore, boolean before) {
		String sql = before
				? "select max(rank) from workitem.artifact where rank < :rank and id <> :ignore"
				: "select min(rank) from workitem.artifact where rank > :rank and id <> :ignore";
		// max/min sem linhas devolve nulo: lido pela coluna para não falhar na conversão.
		return jdbc.sql(sql).param("rank", rank).param("ignore", ignore)
			.query((rs, n) -> rs.getBigDecimal(1)).list().stream().filter(java.util.Objects::nonNull).findFirst();
	}

	void setRank(UUID id, java.math.BigDecimal rank) {
		jdbc.sql("update workitem.artifact set rank = :rank, updated_at = now() where id = :id")
			.param("id", id).param("rank", rank).update();
	}

	/** O artefato, ou um ancestral dele, está numa das unidades (o item aparece num quadro dessa subárvore)? */
	boolean withinUnits(UUID artifactId, Collection<UUID> units) {
		return jdbc.sql("""
				select exists (select 1 from workitem.artifact a where a.id = :id and exists (
				  select 1 from workitem.artifact w where w.org_unit_id = any(cast(:units as uuid[])) and a.path like w.path || '%'))
				""")
			.param("id", artifactId)
			.param("units", array(units))
			.query(Boolean.class)
			.single();
	}

	/** Próximo número do prefixo (1, 2, 3...), atômico mesmo com criações simultâneas. */
	long nextNumber(String prefix) {
		return jdbc.sql("""
				insert into workitem.key_sequence (key_prefix, next_value) values (:prefix, 2)
				on conflict (key_prefix) do update set next_value = workitem.key_sequence.next_value + 1
				returning next_value - 1
				""")
			.param("prefix", prefix)
			.query(Long.class)
			.single();
	}

	void insert(Row r, UUID createdBy) {
		jdbc.sql("""
				insert into workitem.artifact (id, key, type_id, parent_id, path, org_unit_id, status_id, title, description,
				  owner_id, technical_lead_id, qa_owner_id, assignee_id, size, custom_fields, created_by)
				values (:id, :key, :type, :parent, :path, :unit, :status, :title, :description,
				  :owner, :tl, :qa, :assignee, :size, cast(:custom as jsonb), :by)
				""")
			.param("id", r.id()).param("key", r.key()).param("type", r.typeId()).param("parent", r.parentId())
			.param("path", r.path()).param("unit", r.orgUnitId()).param("status", r.statusId())
			.param("title", r.title()).param("description", r.description())
			.param("owner", r.ownerId()).param("tl", r.technicalLeadId()).param("qa", r.qaOwnerId())
			.param("assignee", r.assigneeId()).param("size", r.size()).param("custom", r.customFields())
			.param("by", createdBy)
			.update();
	}

	/** @return false se a versão não confere (concorrência otimista, SSD 1.5) */
	boolean update(Row r, int expectedVersion) {
		return jdbc.sql("""
				update workitem.artifact set parent_id = :parent, path = :path, title = :title, description = :description,
				  owner_id = :owner, technical_lead_id = :tl, qa_owner_id = :qa, assignee_id = :assignee, size = :size,
				  custom_fields = cast(:custom as jsonb), blocked = :blocked, blocked_reason = :reason,
				  version = version + 1, updated_at = now()
				 where id = :id and version = :version
				""")
			.param("id", r.id()).param("parent", r.parentId()).param("path", r.path())
			.param("title", r.title()).param("description", r.description())
			.param("owner", r.ownerId()).param("tl", r.technicalLeadId()).param("qa", r.qaOwnerId())
			.param("assignee", r.assigneeId()).param("size", r.size()).param("custom", r.customFields())
			.param("blocked", r.blocked()).param("reason", r.blockedReason())
			.param("version", expectedVersion)
			.update() == 1;
	}

	/** Novo status, com concorrência otimista. @return false se a versão não confere */
	boolean changeStatus(UUID id, UUID statusId, int expectedVersion) {
		return jdbc.sql("""
				update workitem.artifact set status_id = :status, version = version + 1, updated_at = now()
				 where id = :id and version = :version
				""")
			.param("id", id)
			.param("status", statusId)
			.param("version", expectedVersion)
			.update() == 1;
	}

	/** Troca o prefixo do caminho de toda a subárvore quando o pai muda. */
	void movePaths(String oldPath, String newPath) {
		jdbc.sql("""
				update workitem.artifact set path = :new || substring(path from char_length(:old) + 1)
				 where path like :old || '%' and path <> :old
				""")
			.param("old", oldPath)
			.param("new", newPath)
			.update();
	}

	void archive(UUID id) {
		jdbc.sql("update workitem.artifact set archived = true, version = version + 1, updated_at = now() where id = :id")
			.param("id", id)
			.update();
	}

	private static UUID[] array(Collection<UUID> ids) {
		return ids.toArray(UUID[]::new);
	}

	private static Row row(ResultSet rs) throws SQLException {
		return new Row(rs.getObject("id", UUID.class), rs.getString("key"), rs.getObject("type_id", UUID.class),
			rs.getObject("parent_id", UUID.class), rs.getString("path"), rs.getObject("org_unit_id", UUID.class),
			rs.getObject("status_id", UUID.class), rs.getString("title"), rs.getString("description"),
			rs.getObject("owner_id", UUID.class), rs.getObject("technical_lead_id", UUID.class),
			rs.getObject("qa_owner_id", UUID.class), rs.getObject("assignee_id", UUID.class), rs.getString("size"),
			rs.getString("custom_fields"), rs.getBoolean("archived"), rs.getInt("version"),
			instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("child_count"), rs.getBoolean("blocked"),
			rs.getString("blocked_reason"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	/** Filtros de {@code GET /artifacts} (SSD 3.4) já resolvidos para ids. {@code null} ignora o critério. */
	record Filter(Collection<UUID> workspaceUnits, Collection<UUID> typeIds, Collection<UUID> statusIds, UUID orgUnitId,
			UUID parentId, UUID ownerId, UUID assigneeId, String q, boolean archived, boolean byRank) {
	}

	record Row(UUID id, String key, UUID typeId, UUID parentId, String path, UUID orgUnitId, UUID statusId, String title,
			String description, UUID ownerId, UUID technicalLeadId, UUID qaOwnerId, UUID assigneeId, String size,
			String customFields, boolean archived, int version, Instant createdAt, Instant updatedAt, long childCount,
			boolean blocked, String blockedReason) {

		/** Ids dos ancestrais, da raiz até o pai, lidos do {@code path}. */
		List<UUID> ancestorIds() {
			List<UUID> all = Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
			return all.subList(0, all.size() - 1);
		}
	}
}
