package br.com.aquaqe.platform.configuration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.aquaqe.platform.organization.OrgUnits;

/** Acesso às tabelas do schema {@code configuration}; implementa a API pública {@link ArtifactConfiguration}. */
@Repository
class ConfigurationRepository implements ArtifactConfiguration {

	private static final String STATUS_COLUMNS = "s.id, s.key, s.name, s.phase, s.category, s.position";

	private final JdbcClient jdbc;
	private final OrgUnits orgUnits;

	ConfigurationRepository(JdbcClient jdbc, OrgUnits orgUnits) {
		this.jdbc = jdbc;
		this.orgUnits = orgUnits;
	}

	@Override
	public List<ArtifactType> types() {
		Map<UUID, Set<String>> parents = new java.util.HashMap<>();
		jdbc.sql("""
				select tp.type_id, p.key from configuration.artifact_type_parent tp
				  join configuration.artifact_type p on p.id = tp.parent_type_id
				""")
			.query((rs, n) -> Map.entry(rs.getObject("type_id", UUID.class), rs.getString("key")))
			.list()
			.forEach(e -> parents.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(e.getValue()));
		return jdbc.sql("""
				select id, key, level, name, key_prefix, parent_required from configuration.artifact_type
				 order by case level when 'FL3' then 1 when 'FL2' then 2 else 3 end, key
				""")
			.query((rs, n) -> {
				UUID id = rs.getObject("id", UUID.class);
				return new ArtifactType(id, rs.getString("key"), rs.getString("level"), rs.getString("name"),
					rs.getString("key_prefix"), rs.getBoolean("parent_required"), Set.copyOf(parents.getOrDefault(id, Set.of())));
			})
			.list();
	}

	@Override
	public Optional<ArtifactType> type(String key) {
		return types().stream().filter(t -> t.key().equals(key)).findFirst();
	}

	@Override
	public Optional<ArtifactType> type(UUID id) {
		return types().stream().filter(t -> t.id().equals(id)).findFirst();
	}

	@Override
	public List<CustomField> customFields(String typeKey) {
		return jdbc.sql("""
				select f.id, t.key as type_key, f.key, f.label, f.field_type, f.position,
				       array(select jsonb_array_elements_text(f.options)) as options
				  from configuration.custom_field f
				  join configuration.artifact_type t on t.id = f.type_id
				 where cast(:type as varchar) is null or t.key = cast(:type as varchar)
				 order by t.key, f.position, f.key
				""")
			.param("type", typeKey)
			.query((rs, n) -> new CustomField(rs.getObject("id", UUID.class), rs.getString("type_key"), rs.getString("key"),
				rs.getString("label"), rs.getString("field_type"), List.of((String[]) rs.getArray("options").getArray()),
				rs.getInt("position")))
			.list();
	}

	@Override
	public Status initialStatus(String typeKey, UUID orgUnitId) {
		UUID workflowId = effectiveWorkflowId(orgUnitId, typeKey)
			.orElseThrow(() -> new IllegalStateException("Sem workflow para " + typeKey));
		return jdbc.sql("select " + STATUS_COLUMNS + """
				 from configuration.workflow_status s
				 where s.workflow_id = :workflow and s.position is not null
				 order by s.position limit 1
				""")
			.param("workflow", workflowId)
			.query((rs, n) -> status(rs))
			.single();
	}

	@Override
	public Optional<UUID> effectiveWorkflowId(UUID orgUnitId, String typeKey) {
		List<UUID> lineage = orgUnitId == null ? List.of(orgUnits.rootId()) : orgUnits.lineage(orgUnitId);
		if (lineage.isEmpty()) {
			return Optional.empty();
		}
		Map<UUID, UUID> byUnit = new java.util.HashMap<>();
		jdbc.sql("""
				select w.id, w.org_unit_id from configuration.workflow w
				  join configuration.workflow_artifact_type wt on wt.workflow_id = w.id
				  join configuration.artifact_type t on t.id = wt.artifact_type_id
				 where w.active and t.key = :type and w.org_unit_id in (:units)
				""")
			.param("type", typeKey)
			.param("units", lineage)
			.query((rs, n) -> Map.entry(rs.getObject("org_unit_id", UUID.class), rs.getObject("id", UUID.class)))
			.list()
			.forEach(e -> byUnit.put(e.getKey(), e.getValue()));
		for (int i = lineage.size() - 1; i >= 0; i--) {
			UUID found = byUnit.get(lineage.get(i));
			if (found != null) {
				return Optional.of(found);
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<WorkflowScope> workflowScope(UUID workflowId) {
		return jdbc.sql("""
				select id, family_id, org_unit_id, active, version_number, name from configuration.workflow where id = :id
				""")
			.param("id", workflowId)
			.query((rs, n) -> new WorkflowScope(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class),
				rs.getObject("org_unit_id", UUID.class), rs.getBoolean("active"), rs.getInt("version_number"),
				rs.getString("name"), List.of(), List.of()))
			.optional()
			.map(w -> new WorkflowScope(w.id(), w.familyId(), w.orgUnitId(), w.active(), w.versionNumber(), w.name(),
				typesOf(w.id()), statusesOf(w.id())));
	}

	@Override
	public Map<UUID, Status> statuses(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select " + STATUS_COLUMNS + " from configuration.workflow_status s where s.id in (:ids)")
			.param("ids", ids)
			.query((rs, n) -> status(rs))
			.list()
			.stream()
			.collect(Collectors.toMap(Status::id, Function.identity()));
	}

	@Override
	public Set<UUID> statusIds(String key, String category) {
		return Set.copyOf(jdbc.sql("""
				select s.id from configuration.workflow_status s
				 where (cast(:key as varchar) is null or s.key = cast(:key as varchar))
				   and (cast(:category as varchar) is null or s.category = cast(:category as varchar))
				""")
			.param("key", key)
			.param("category", category)
			.query(UUID.class)
			.list());
	}

	@Override
	public List<Status> sequenceOfLevel(String level, UUID orgUnitId) {
		Set<UUID> units = relevantUnits(orgUnitId);
		return jdbc.sql("""
				select s.key, min(s.name) as name, min(s.phase) as phase, min(s.category) as category,
				       min(s.position) as position
				  from configuration.workflow_status s
				  join configuration.workflow w on w.id = s.workflow_id and w.active
				  join configuration.workflow_artifact_type wt on wt.workflow_id = w.id
				  join configuration.artifact_type t on t.id = wt.artifact_type_id
				 where t.level = :level and s.position is not null and w.org_unit_id in (:units)
				 group by s.key
				 order by min(s.position), s.key
				""")
			.param("level", level)
			.param("units", units)
			.query((rs, n) -> new Status(null, rs.getString("key"), rs.getString("name"), rs.getString("phase"),
				rs.getString("category"), rs.getObject("position", Integer.class)))
			.list();
	}

	@Override
	public List<StatusTransition> transitionsFrom(UUID statusId) {
		List<Map.Entry<UUID, UUID>> pairs = jdbc.sql("""
				select tr.id, tr.to_status_id from configuration.workflow_transition tr
				  join configuration.workflow_status t on t.id = tr.to_status_id
				 where tr.from_status_id = :from
				 order by t.position nulls last
				""")
			.param("from", statusId)
			.query((rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getObject("to_status_id", UUID.class)))
			.list();
		List<UUID> ids = new java.util.ArrayList<>(pairs.stream().map(Map.Entry::getValue).toList());
		ids.add(statusId);
		Map<UUID, Status> statuses = statuses(ids);
		return pairs.stream()
			.map(p -> new StatusTransition(p.getKey(), statuses.get(statusId), statuses.get(p.getValue())))
			.toList();
	}

	@Override
	public Optional<TransitionScope> transition(UUID transitionId) {
		return jdbc.sql("""
				select tr.id, tr.from_status_id, tr.to_status_id, w.id as workflow_id, w.name as workflow_name
				  from configuration.workflow_transition tr
				  join configuration.workflow w on w.id = tr.workflow_id
				 where tr.id = :id
				""")
			.param("id", transitionId)
			.query((rs, n) -> new Object[] { rs.getObject("from_status_id", UUID.class),
				rs.getObject("to_status_id", UUID.class), rs.getObject("workflow_id", UUID.class),
				rs.getString("workflow_name") })
			.optional()
			.map(row -> {
				UUID from = (UUID) row[0];
				UUID to = (UUID) row[1];
				UUID workflowId = (UUID) row[2];
				Map<UUID, Status> statuses = statuses(List.of(from, to));
				List<String> types = jdbc.sql("""
						select t.key from configuration.workflow_artifact_type wt
						  join configuration.artifact_type t on t.id = wt.artifact_type_id
						 where wt.workflow_id = :id order by t.key
						""")
					.param("id", workflowId)
					.query(String.class)
					.list();
				return new TransitionScope(new StatusTransition(transitionId, statuses.get(from), statuses.get(to)),
					workflowId, (String) row[3], types);
			});
	}

	/** A unidade, as de cima e as de baixo: os workflows dessas unidades valem para os itens que ela vê (#49). */
	private Set<UUID> relevantUnits(UUID orgUnitId) {
		Set<UUID> units = new HashSet<>();
		if (orgUnitId != null) {
			units.addAll(orgUnits.lineage(orgUnitId));
			units.addAll(orgUnits.subtrees(List.of(orgUnitId)));
		}
		if (units.isEmpty()) {
			units.add(orgUnits.rootId());
		}
		return units;
	}

	/**
	 * Workflows ativos (filtro por tipo). Com {@code orgUnitId}, só os que valem para a unidade ou para as de baixo: os
	 * da linhagem e os das unidades da subárvore (#49).
	 */
	List<Workflow> workflows(String artifactType, UUID orgUnitId) {
		Set<UUID> units = orgUnitId == null ? null : relevantUnits(orgUnitId);
		return jdbc.sql("""
				select w.id from configuration.workflow w
				 where w.active and (cast(:type as varchar) is null or exists (
				   select 1 from configuration.workflow_artifact_type wt
				     join configuration.artifact_type t on t.id = wt.artifact_type_id
				    where wt.workflow_id = w.id and t.key = cast(:type as varchar)))
				   and (:all or w.org_unit_id in (:units))
				 order by w.name, w.published_at
				""")
			.param("type", artifactType)
			.param("all", units == null)
			.param("units", units == null ? List.of(UUID.randomUUID()) : units)
			.query(UUID.class)
			.list()
			.stream()
			.map(id -> workflow(id).orElseThrow())
			.toList();
	}

	Optional<Workflow> workflow(UUID id) {
		return jdbc.sql("""
				select id, family_id, name, version_number, org_unit_id, active, published_at
				  from configuration.workflow where id = :id
				""")
			.param("id", id)
			.query((rs, n) -> new Object[] { rs.getObject("family_id", UUID.class), rs.getString("name"),
				rs.getInt("version_number"), rs.getObject("org_unit_id", UUID.class), rs.getBoolean("active"),
				rs.getObject("published_at", java.time.OffsetDateTime.class).toInstant() })
			.optional()
			.map(row -> workflow(id, (UUID) row[0], (String) row[1], (int) row[2], (UUID) row[3], (boolean) row[4],
				(java.time.Instant) row[5]));
	}

	List<String> typesOf(UUID workflowId) {
		return jdbc.sql("""
				select t.key from configuration.workflow_artifact_type wt
				  join configuration.artifact_type t on t.id = wt.artifact_type_id
				 where wt.workflow_id = :id order by t.key
				""")
			.param("id", workflowId)
			.query(String.class)
			.list();
	}

	List<Status> statusesOf(UUID workflowId) {
		return jdbc.sql("select " + STATUS_COLUMNS
				+ " from configuration.workflow_status s where s.workflow_id = :id order by s.position nulls last, s.name")
			.param("id", workflowId)
			.query((rs, n) -> status(rs))
			.list();
	}

	private Workflow workflow(UUID id, UUID familyId, String name, int version, UUID orgUnitId, boolean active,
			java.time.Instant publishedAt) {
		List<String> types = typesOf(id);
		List<Status> statuses = statusesOf(id);
		List<Transition> transitions = jdbc.sql("""
				select tr.id, f.key as from_key, t.key as to_key from configuration.workflow_transition tr
				  join configuration.workflow_status f on f.id = tr.from_status_id
				  join configuration.workflow_status t on t.id = tr.to_status_id
				 where tr.workflow_id = :id
				 order by f.position nulls last, t.position nulls last
				""")
			.param("id", id)
			.query((rs, n) -> new Transition(rs.getObject("id", UUID.class), rs.getString("from_key"), rs.getString("to_key"),
				false))
			.list();
		OrgUnitRef unit = orgUnits.find(orgUnitId).map(u -> new OrgUnitRef(u.id(), u.name())).orElse(null);
		return new Workflow(id, familyId, name, types, version, active, unit, publishedAt, statuses, transitions);
	}

	// ------------------------------------------------------------------------------------------ escrita (#49)

	/** Workflows ativos de que a unidade é dona, com os tipos de cada um. */
	Map<UUID, List<String>> ownedBy(UUID orgUnitId) {
		Map<UUID, List<String>> result = new java.util.LinkedHashMap<>();
		jdbc.sql("""
				select w.id, t.key from configuration.workflow w
				  join configuration.workflow_artifact_type wt on wt.workflow_id = w.id
				  join configuration.artifact_type t on t.id = wt.artifact_type_id
				 where w.active and w.org_unit_id = :unit order by w.name, t.key
				""")
			.param("unit", orgUnitId)
			.query((rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("key")))
			.list()
			.forEach(e -> result.computeIfAbsent(e.getKey(), k -> new java.util.ArrayList<>()).add(e.getValue()));
		return result;
	}

	void insertWorkflow(UUID id, UUID familyId, String name, int version, UUID orgUnitId, UUID user) {
		jdbc.sql("""
				insert into configuration.workflow (id, family_id, name, version_number, org_unit_id, active, published_by)
				values (:id, :family, :name, :version, :unit, true, :user)
				""")
			.param("id", id).param("family", familyId).param("name", name).param("version", version)
			.param("unit", orgUnitId).param("user", user)
			.update();
	}

	void insertTypes(UUID workflowId, List<String> typeKeys) {
		for (String key : typeKeys) {
			jdbc.sql("""
					insert into configuration.workflow_artifact_type (workflow_id, artifact_type_id)
					select :workflow, id from configuration.artifact_type where key = :key
					""")
				.param("workflow", workflowId).param("key", key)
				.update();
		}
	}

	UUID insertStatus(UUID workflowId, String key, String name, String phase, String category, Integer position) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				insert into configuration.workflow_status (id, workflow_id, key, name, phase, category, position)
				values (:id, :workflow, :key, :name, :phase, :category, :position)
				""")
			.param("id", id).param("workflow", workflowId).param("key", key).param("name", name).param("phase", phase)
			.param("category", category).param("position", position)
			.update();
		return id;
	}

	UUID insertTransition(UUID workflowId, UUID fromId, UUID toId) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				insert into configuration.workflow_transition (id, workflow_id, from_status_id, to_status_id)
				values (:id, :workflow, :from, :to)
				""")
			.param("id", id).param("workflow", workflowId).param("from", fromId).param("to", toId)
			.update();
		return id;
	}

	/** Desativa a versão; devolve falso se ela já não era a ativa (outra publicação chegou antes). */
	boolean deactivate(UUID workflowId) {
		return jdbc.sql("update configuration.workflow set active = false where id = :id and active")
			.param("id", workflowId)
			.update() == 1;
	}

	private static Status status(ResultSet rs) throws SQLException {
		return new Status(rs.getObject("id", UUID.class), rs.getString("key"), rs.getString("name"), rs.getString("phase"),
			rs.getString("category"), rs.getObject("position", Integer.class));
	}

	/** Versão de workflow; {@code familyId} agrupa as versões, e só a mais recente fica {@code active} (#49). */
	record Workflow(UUID id, UUID familyId, String name, List<String> artifactTypes, int versionNumber, boolean active,
			OrgUnitRef orgUnit, java.time.Instant publishedAt, List<Status> statuses, List<Transition> transitions) {
	}

	record OrgUnitRef(UUID id, String name) {
	}

	/** {@code hasGate} fica sempre falso aqui: os gates são do módulo governance ({@code GET /gates}, #78). */
	record Transition(UUID id, String from, String to, boolean hasGate) {
	}
}
