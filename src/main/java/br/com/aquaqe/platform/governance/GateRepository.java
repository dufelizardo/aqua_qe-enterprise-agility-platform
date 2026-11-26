package br.com.aquaqe.platform.governance;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.ObjectMapper;

/** Tabelas do schema {@code governance}: gates, regras e decisões de aprovação (SSD 5; #78). */
@Repository
class GateRepository {

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	GateRepository(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	Optional<GateRow> gateOf(UUID transitionId) {
		return jdbc.sql("select id, transition_id, name, updated_at from governance.gate where transition_id = :t")
			.param("t", transitionId)
			.query((rs, n) -> new GateRow(rs.getObject("id", UUID.class), rs.getObject("transition_id", UUID.class),
				rs.getString("name"), rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
			.optional();
	}

	List<GateRow> gates() {
		return jdbc.sql("select id, transition_id, name, updated_at from governance.gate order by name")
			.query((rs, n) -> new GateRow(rs.getObject("id", UUID.class), rs.getObject("transition_id", UUID.class),
				rs.getString("name"), rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	List<RuleRow> rules(UUID gateId) {
		return jdbc.sql("""
				select id, position, type, params::text as params, message from governance.gate_rule
				 where gate_id = :gate order by position
				""")
			.param("gate", gateId)
			.query((rs, n) -> new RuleRow(rs.getObject("id", UUID.class), rs.getInt("position"), rs.getString("type"),
				map(rs.getString("params")), rs.getString("message")))
			.list();
	}

	long ruleCount(UUID gateId) {
		return jdbc.sql("select count(*) from governance.gate_rule where gate_id = :gate").param("gate", gateId)
			.query(Long.class).single();
	}

	/** Cria ou renomeia o gate da transição e troca todas as regras. @return o id do gate */
	UUID save(UUID transitionId, String name, List<NewRule> rules, UUID user) {
		UUID gateId = jdbc.sql("""
				insert into governance.gate (transition_id, name, updated_by) values (:t, :name, :user)
				on conflict (transition_id) do update set name = :name, updated_by = :user, updated_at = now()
				returning id
				""")
			.param("t", transitionId).param("name", name).param("user", user)
			.query(UUID.class).single();
		jdbc.sql("delete from governance.gate_rule where gate_id = :gate").param("gate", gateId).update();
		for (int i = 0; i < rules.size(); i++) {
			NewRule rule = rules.get(i);
			jdbc.sql("""
					insert into governance.gate_rule (gate_id, position, type, params, message)
					values (:gate, :position, :type, cast(:params as jsonb), :message)
					""")
				.param("gate", gateId).param("position", i).param("type", rule.type())
				.param("params", json.writeValueAsString(rule.params())).param("message", rule.message())
				.update();
		}
		return gateId;
	}

	boolean delete(UUID transitionId) {
		return jdbc.sql("delete from governance.gate where transition_id = :t").param("t", transitionId).update() > 0;
	}

	void decide(UUID artifactId, UUID ruleId, UUID statusId, String decision, String comment, UUID user) {
		jdbc.sql("""
				insert into governance.approval (artifact_id, rule_id, status_id, decision, comment, decided_by)
				values (:artifact, :rule, :status, :decision, :comment, :user)
				""")
			.param("artifact", artifactId).param("rule", ruleId).param("status", statusId).param("decision", decision)
			.param("comment", comment).param("user", user)
			.update();
	}

	/** A última decisão da regra para o artefato, dada no status informado. */
	Optional<Decision> lastDecision(UUID artifactId, UUID ruleId, UUID statusId) {
		return jdbc.sql("""
				select decision, comment, decided_by, decided_at from governance.approval
				 where artifact_id = :artifact and rule_id = :rule and status_id = :status
				 order by decided_at desc limit 1
				""")
			.param("artifact", artifactId).param("rule", ruleId).param("status", statusId)
			.query((rs, n) -> new Decision(rs.getString("decision"), rs.getString("comment"),
				rs.getObject("decided_by", UUID.class), rs.getObject("decided_at", OffsetDateTime.class).toInstant()))
			.optional();
	}

	/** Todas as decisões do artefato, da mais recente para a mais antiga. */
	List<StoredDecision> decisions(UUID artifactId) {
		return jdbc.sql("""
				select id, rule_id, decision, comment, decided_by, decided_at from governance.approval
				 where artifact_id = :artifact order by decided_at desc
				""")
			.param("artifact", artifactId)
			.query((rs, n) -> new StoredDecision(rs.getObject("id", UUID.class), rs.getObject("rule_id", UUID.class),
				rs.getString("decision"), rs.getString("comment"), rs.getObject("decided_by", UUID.class),
				rs.getObject("decided_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	/** Gates das transições informadas, por transição. */
	Map<UUID, GateRow> gatesOf(Collection<UUID> transitionIds) {
		if (transitionIds.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, transition_id, name, updated_at from governance.gate where transition_id in (:ids)")
			.param("ids", transitionIds)
			.query((rs, n) -> new GateRow(rs.getObject("id", UUID.class), rs.getObject("transition_id", UUID.class),
				rs.getString("name"), rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
			.list().stream()
			.collect(java.util.stream.Collectors.toMap(GateRow::transitionId, g -> g));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(String value) {
		return value == null ? Map.of() : json.readValue(value, Map.class);
	}

	record GateRow(UUID id, UUID transitionId, String name, Instant updatedAt) {
	}

	record RuleRow(UUID id, int position, String type, Map<String, Object> params, String message) {
	}

	record NewRule(String type, Map<String, Object> params, String message) {
	}

	record Decision(String decision, String comment, UUID decidedBy, Instant decidedAt) {
	}

	record StoredDecision(UUID id, UUID ruleId, String decision, String comment, UUID decidedBy, Instant decidedAt) {
	}
}
