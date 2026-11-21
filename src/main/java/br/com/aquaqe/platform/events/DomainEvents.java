package br.com.aquaqe.platform.events;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.shared.Correlation;
import tools.jackson.databind.ObjectMapper;

/**
 * Publicação de eventos de domínio (SAD 7.4; ADR-06): grava o evento na outbox na <b>mesma transação</b> da mudança. Se
 * a mudança for desfeita, o evento também é; nenhum evento se perde.
 */
@Service
public class DomainEvents {

	private final JdbcClient jdbc;
	private final ObjectMapper json;

	DomainEvents(JdbcClient jdbc, ObjectMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	/** Publica um evento sem valores anterior e posterior. */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID publish(String type, UUID actorId, UUID orgUnitId, Aggregate aggregate, Map<String, Object> payload) {
		return publish(type, actorId, orgUnitId, aggregate, payload, null, null);
	}

	/**
	 * Publica um evento na transação corrente. {@code before} e {@code after} vão para a trilha de auditoria; nunca devem
	 * conter senhas ou tokens (SAD 7.5).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UUID publish(String type, UUID actorId, UUID orgUnitId, Aggregate aggregate, Map<String, Object> payload,
			Map<String, Object> before, Map<String, Object> after) {
		// Micros: é a precisão do PostgreSQL; assim o hash calculado e o relido conferem.
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), type, now, actorId, orgUnitId,
			Correlation.current().orElse(null), aggregate, payload == null ? Map.of() : payload, before, after);
		jdbc.sql("""
				insert into events.outbox_event (event_id, type, occurred_at, envelope)
				values (:id, :type, :at, cast(:envelope as jsonb))
				""")
			.param("id", envelope.eventId())
			.param("type", type)
			.param("at", java.sql.Timestamp.from(now))
			.param("envelope", json.writeValueAsString(envelope))
			.update();
		return envelope.eventId();
	}

	/**
	 * Publica fora da transação corrente, numa transação própria: para o evento de uma tentativa que vai ser desfeita, como
	 * a transição bloqueada pelo gate (SSD 4.2).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public UUID publishSeparately(String type, UUID actorId, UUID orgUnitId, Aggregate aggregate,
			Map<String, Object> payload) {
		return publish(type, actorId, orgUnitId, aggregate, payload, null, null);
	}
}
