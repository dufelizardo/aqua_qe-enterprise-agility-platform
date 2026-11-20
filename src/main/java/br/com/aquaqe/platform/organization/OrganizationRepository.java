package br.com.aquaqe.platform.organization;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso às tabelas do schema {@code organization}; também é a implementação da API pública {@link OrgUnits}. */
@Repository
class OrganizationRepository implements OrgUnits {

	private static final String UNIT_SELECT = """
		select u.id, u.name, u.parent_id, u.path, u.active, u.version,
		       l.key as level_key, l.name as level_name, l.position as level_position, l.flight_level
		  from organization.org_unit u
		  join organization.org_level l on l.id = u.level_id
		""";

	private static final String FILTER = """
		 where (cast(:parent as uuid) is null or u.parent_id = cast(:parent as uuid))
		   and (cast(:level as varchar) is null or l.key = cast(:level as varchar))
		""";

	private final JdbcClient jdbc;

	OrganizationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<Level> levels() {
		return jdbc.sql("select key, name, position, flight_level from organization.org_level order by position")
			.query((rs, n) -> level(rs))
			.list();
	}

	Optional<Level> level(String key) {
		return jdbc.sql("select key, name, position, flight_level from organization.org_level where key = :key")
			.param("key", key)
			.query((rs, n) -> level(rs))
			.optional();
	}

	Optional<Unit> unit(UUID id) {
		return jdbc.sql(UNIT_SELECT + " where u.id = :id")
			.param("id", id)
			.query((rs, n) -> unit(rs))
			.optional();
	}

	List<Unit> units(UUID parentId, String levelKey, long offset, int limit) {
		return jdbc.sql(UNIT_SELECT + FILTER + " order by l.position, u.name, u.id offset :offset limit :limit")
			.param("parent", parentId)
			.param("level", levelKey)
			.param("offset", offset)
			.param("limit", limit)
			.query((rs, n) -> unit(rs))
			.list();
	}

	long countUnits(UUID parentId, String levelKey) {
		return jdbc.sql("select count(*) from organization.org_unit u join organization.org_level l on l.id = u.level_id"
				+ FILTER)
			.param("parent", parentId)
			.param("level", levelKey)
			.query(Long.class)
			.single();
	}

	/** Referências às unidades pedidas, na mesma ordem dos ids (usada para o caminho até a raiz). */
	List<UnitRef> refs(List<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<UUID, UnitRef> found = jdbc.sql("""
				select u.id, u.name, l.key as level_key from organization.org_unit u
				  join organization.org_level l on l.id = u.level_id where u.id in (:ids)
				""")
			.param("ids", ids)
			.query((rs, n) -> ref(rs))
			.list()
			.stream()
			.collect(Collectors.toMap(UnitRef::id, Function.identity()));
		return ids.stream().map(found::get).filter(r -> r != null).toList();
	}

	UUID insertUnit(String levelKey, String name, Unit parent) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				insert into organization.org_unit (id, level_id, name, parent_id, path)
				select :id, l.id, :name, :parent, :path from organization.org_level l where l.key = :level
				""")
			.param("id", id)
			.param("name", name)
			.param("parent", parent.id())
			.param("path", parent.path() + id + "/")
			.param("level", levelKey)
			.update();
		return id;
	}

	/** @return false se a versão não confere (concorrência otimista, SSD 1.5) */
	boolean updateUnit(UUID id, String name, boolean active, int expectedVersion) {
		return jdbc.sql("""
				update organization.org_unit
				   set name = :name, active = :active, version = version + 1, updated_at = now()
				 where id = :id and version = :version
				""")
			.param("id", id)
			.param("name", name)
			.param("active", active)
			.param("version", expectedVersion)
			.update() == 1;
	}

	/** Unidades que a unidade atende. */
	List<UnitRef> serves(UUID unitId) {
		return linkRefs("k.serves_unit_id", "k.unit_id", unitId);
	}

	/** Unidades que atendem à unidade. */
	List<UnitRef> servedBy(UUID unitId) {
		return linkRefs("k.unit_id", "k.serves_unit_id", unitId);
	}

	void insertLink(UUID unitId, UUID servesUnitId, UUID createdBy) {
		jdbc.sql("""
				insert into organization.org_unit_link (unit_id, serves_unit_id, created_by)
				values (:unit, :serves, :by) on conflict do nothing
				""")
			.param("unit", unitId)
			.param("serves", servesUnitId)
			.param("by", createdBy)
			.update();
	}

	void deleteLink(UUID unitId, UUID servesUnitId) {
		jdbc.sql("delete from organization.org_unit_link where unit_id = :unit and serves_unit_id = :serves")
			.param("unit", unitId)
			.param("serves", servesUnitId)
			.update();
	}

	@Override
	public Optional<OrgUnitSummary> find(UUID id) {
		return unit(id).map(u -> new OrgUnitSummary(u.id(), u.name(), u.level().key(), u.level().flightLevel(),
			u.parentId(), u.active()));
	}

	@Override
	public List<UUID> lineage(UUID id) {
		return unit(id).map(Unit::lineage).orElse(List.of());
	}

	@Override
	public UUID rootId() {
		return jdbc.sql("select id from organization.org_unit where parent_id is null").query(UUID.class).single();
	}

	@Override
	public Set<UUID> subtrees(Collection<UUID> roots) {
		if (roots.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("""
				select u.id from organization.org_unit u
				 where exists (select 1 from organization.org_unit r where r.id in (:roots) and u.path like r.path || '%')
				""")
			.param("roots", roots)
			.query(UUID.class)
			.list());
	}

	@Override
	public boolean anyServes(Collection<UUID> from, Collection<UUID> to) {
		if (from.isEmpty() || to.isEmpty()) {
			return false;
		}
		return jdbc.sql("""
				select exists (select 1 from organization.org_unit_link
				 where unit_id in (:from) and serves_unit_id in (:to))
				""")
			.param("from", from)
			.param("to", to)
			.query(Boolean.class)
			.single();
	}

	private List<UnitRef> linkRefs(String shownColumn, String filterColumn, UUID unitId) {
		return jdbc.sql("select u.id, u.name, l.key as level_key from organization.org_unit_link k"
				+ " join organization.org_unit u on u.id = " + shownColumn
				+ " join organization.org_level l on l.id = u.level_id"
				+ " where " + filterColumn + " = :id order by u.name, u.id")
			.param("id", unitId)
			.query((rs, n) -> ref(rs))
			.list();
	}

	private static UnitRef ref(ResultSet rs) throws SQLException {
		return new UnitRef(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("level_key"));
	}

	private static Level level(ResultSet rs) throws SQLException {
		return new Level(rs.getString("key"), rs.getString("name"), rs.getInt("position"), rs.getString("flight_level"));
	}

	private static Unit unit(ResultSet rs) throws SQLException {
		return new Unit(rs.getObject("id", UUID.class), rs.getString("name"), rs.getObject("parent_id", UUID.class),
			rs.getString("path"), rs.getBoolean("active"), rs.getInt("version"),
			new Level(rs.getString("level_key"), rs.getString("level_name"), rs.getInt("level_position"),
				rs.getString("flight_level")));
	}

	record Level(String key, String name, int position, String flightLevel) {
	}

	record UnitRef(UUID id, String name, String levelKey) {
	}

	record Unit(UUID id, String name, UUID parentId, String path, boolean active, int version, Level level) {

		/** Ids da raiz até esta unidade, lidos do {@code path}. */
		List<UUID> lineage() {
			return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).map(UUID::fromString).toList();
		}

		/** Verdadeiro se uma unidade é ancestral da outra, ou se são a mesma. */
		boolean sameLineAs(Unit other) {
			return path.startsWith(other.path) || other.path.startsWith(path);
		}
	}
}
