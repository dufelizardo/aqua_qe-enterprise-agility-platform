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

/** Acesso às tabelas do schema {@code configuration}; implementa a API pública {@link ArtifactConfiguration}. */
@Repository
class ConfigurationRepository implements ArtifactConfiguration {

	private static final String STATUS_COLUMNS = "s.id, s.key, s.name, s.phase, s.category, s.position";

	private final JdbcClient jdbc;

	ConfigurationRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
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
	public Status initialStatus(String typeKey) {
		return jdbc.sql("select " + STATUS_COLUMNS + """
				 from configuration.workflow_status s
				 join configuration.workflow w on w.id = s.workflow_id and w.active
				 join configuration.workflow_artifact_type wt on wt.workflow_id = w.id
				 join configuration.artifact_type t on t.id = wt.artifact_type_id
				 where t.key = :type and s.position is not null
				 order by s.position limit 1
				""")
			.param("type", typeKey)
			.query((rs, n) -> status(rs))
			.single();
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
	public List<Status> sequenceOfLevel(String level) {
		return jdbc.sql("""
				select s.key, min(s.name) as name, min(s.phase) as phase, min(s.category) as category,
				       min(s.position) as position
				  from configuration.workflow_status s
				  join configuration.workflow w on w.id = s.workflow_id and w.active
				  join configuration.workflow_artifact_type wt on wt.workflow_id = w.id
				  join configuration.artifact_type t on t.id = wt.artifact_type_id
				 where t.level = :level and s.position is not null
				 group by s.key
				 order by min(s.position), s.key
				""")
			.param("level", level)
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

	List<Workflow> workflows(String artifactType) {
		return jdbc.sql("""
				select w.id, w.name, w.version_number from configuration.workflow w
				 where w.active and (cast(:type as varchar) is null or exists (
				   select 1 from configuration.workflow_artifact_type wt
				     join configuration.artifact_type t on t.id = wt.artifact_type_id
				    where wt.workflow_id = w.id and t.key = cast(:type as varchar)))
				 order by w.name
				""")
			.param("type", artifactType)
			.query((rs, n) -> workflow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("version_number")))
			.list();
	}

	Optional<Workflow> workflow(UUID id) {
		return jdbc.sql("select id, name, version_number from configuration.workflow where id = :id")
			.param("id", id)
			.query((rs, n) -> workflow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("version_number")))
			.optional();
	}

	private Workflow workflow(UUID id, String name, int version) {
		List<String> types = jdbc.sql("""
				select t.key from configuration.workflow_artifact_type wt
				  join configuration.artifact_type t on t.id = wt.artifact_type_id
				 where wt.workflow_id = :id order by t.key
				""")
			.param("id", id)
			.query(String.class)
			.list();
		List<Status> statuses = jdbc.sql("select " + STATUS_COLUMNS
				+ " from configuration.workflow_status s where s.workflow_id = :id order by s.position nulls last")
			.param("id", id)
			.query((rs, n) -> status(rs))
			.list();
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
		return new Workflow(id, name, types, version, statuses, transitions);
	}

	private static Status status(ResultSet rs) throws SQLException {
		return new Status(rs.getObject("id", UUID.class), rs.getString("key"), rs.getString("name"), rs.getString("phase"),
			rs.getString("category"), rs.getObject("position", Integer.class));
	}

	record Workflow(UUID id, String name, List<String> artifactTypes, int versionNumber, List<Status> statuses,
			List<Transition> transitions) {
	}

	/** {@code hasGate} passa a ser calculado com o módulo governance; por ora nenhuma transição tem gate. */
	record Transition(UUID id, String from, String to, boolean hasGate) {
	}
}
