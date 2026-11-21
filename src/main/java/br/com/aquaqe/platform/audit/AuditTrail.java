package br.com.aquaqe.platform.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.events.EventConsumer;
import br.com.aquaqe.platform.events.EventEnvelope;
import tools.jackson.databind.ObjectMapper;

/**
 * Trilha de auditoria (SAD 7.5; SSD 9): consumidor único da outbox, só inserção, encadeado por hash. O {@code hash} é o
 * SHA-256 de {@code prev_hash} concatenado com a forma canônica do registro (campos em ordem fixa, JSON sem espaços e com
 * chaves ordenadas); o primeiro registro usa 64 zeros. Um mesmo {@code eventId} não grava dois registros.
 */
@Component
class AuditTrail implements EventConsumer {

	static final String GENESIS = "0".repeat(64);

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	AuditTrail(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public void accept(EventEnvelope event) {
		boolean known = jdbc.sql("select exists (select 1 from audit.audit_event where event_id = :id)")
			.param("id", event.eventId()).query(Boolean.class).single();
		if (known) {
			return; // reentrega (ADR-06)
		}
		String prev = jdbc.sql("select hash from audit.audit_event order by sequence desc limit 1")
			.query(String.class).optional().orElse(GENESIS);
		Record record = new Record(event.eventId(), event.occurredAt(), event.actorId(), event.type(),
			event.aggregate().type(), event.aggregate().id(), event.aggregate().key(), event.before(), event.after(),
			event.correlationId());
		jdbc.sql("""
				insert into audit.audit_event (event_id, occurred_at, actor_id, action, target_type, target_id, target_key,
				  before, after, correlation_id, prev_hash, hash)
				values (:event, :at, :actor, :action, :type, :target, :key, cast(:before as jsonb), cast(:after as jsonb),
				  :correlation, :prev, :hash)
				""")
			.param("event", record.eventId()).param("at", java.sql.Timestamp.from(record.occurredAt()))
			.param("actor", record.actorId()).param("action", record.action()).param("type", record.targetType())
			.param("target", record.targetId()).param("key", record.targetKey())
			.param("before", record.before() == null ? null : json.writeValueAsString(record.before()))
			.param("after", record.after() == null ? null : json.writeValueAsString(record.after()))
			.param("correlation", record.correlationId()).param("prev", prev).param("hash", hash(prev, record))
			.update();
	}

	/**
	 * Recalcula a cadeia inteira e devolve o primeiro registro inconsistente (hash que não confere ou elo com o anterior
	 * quebrado), se houver (SSD 3.10).
	 */
	Verification verify() {
		List<Stored> rows = jdbc.sql("""
				select sequence, event_id, occurred_at, actor_id, action, target_type, target_id, target_key,
				       before::text as before, after::text as after, correlation_id, prev_hash, hash
				  from audit.audit_event order by sequence
				""")
			.query((rs, n) -> new Stored(rs.getLong("sequence"), new Record(rs.getObject("event_id", UUID.class),
				rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), rs.getObject("actor_id", UUID.class),
				rs.getString("action"), rs.getString("target_type"), rs.getObject("target_id", UUID.class),
				rs.getString("target_key"), map(rs.getString("before")), map(rs.getString("after")),
				rs.getString("correlation_id")), rs.getString("prev_hash"), rs.getString("hash")))
			.list();
		String expectedPrev = GENESIS;
		for (Stored row : rows) {
			if (!row.prevHash().equals(expectedPrev) || !row.hash().equals(hash(row.prevHash(), row.record()))) {
				return new Verification(false, rows.size(), row.sequence());
			}
			expectedPrev = row.hash();
		}
		return new Verification(true, rows.size(), null);
	}

	String hash(String prev, Record record) {
		List<Object> canonical = new ArrayList<>();
		canonical.add(record.eventId().toString());
		canonical.add(record.occurredAt().toString());
		canonical.add(record.actorId() == null ? null : record.actorId().toString());
		canonical.add(record.action());
		canonical.add(record.targetType());
		canonical.add(record.targetId() == null ? null : record.targetId().toString());
		canonical.add(record.targetKey());
		canonical.add(sorted(record.before()));
		canonical.add(sorted(record.after()));
		canonical.add(record.correlationId());
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			byte[] digest = sha.digest((prev + json.writeValueAsString(canonical)).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Chaves em ordem alfabética, recursivamente: a mesma forma na gravação e na verificação. */
	@SuppressWarnings("unchecked")
	private static Object sorted(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> result = new TreeMap<>();
			((Map<String, Object>) map).forEach((k, v) -> result.put(k, sorted(v)));
			return result;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(AuditTrail::sorted).toList();
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(String value) {
		return value == null ? null : json.readValue(value, Map.class);
	}

	record Record(UUID eventId, Instant occurredAt, UUID actorId, String action, String targetType, UUID targetId,
			String targetKey, Map<String, Object> before, Map<String, Object> after, String correlationId) {
	}

	private record Stored(long sequence, Record record, String prevHash, String hash) {
	}

	/** {@code firstInconsistent}: o primeiro registro que não confere, ou nulo quando a trilha está íntegra. */
	record Verification(boolean valid, int checked, Long firstInconsistent) {
	}
}
