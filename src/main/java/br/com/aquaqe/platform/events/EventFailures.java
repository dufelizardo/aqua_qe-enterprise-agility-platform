package br.com.aquaqe.platform.events;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * Fila de falhas dos consumidores que não são a auditoria (#8; REV-03; D-03): lista e reprocessa os eventos que
 * esgotaram as tentativas. A permissão fica com quem chama (o módulo {@code audit}).
 */
@Service
public class EventFailures {

	private final JdbcClient jdbc;
	private final ObjectMapper json;
	private final Map<String, EventConsumer> consumers;
	private final org.springframework.transaction.support.TransactionTemplate savepoint;

	EventFailures(JdbcClient jdbc, ObjectMapper json, List<EventConsumer> consumers,
			org.springframework.transaction.PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.json = json;
		this.savepoint = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
		this.savepoint.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
		this.consumers = consumers.stream().collect(Collectors.toMap(EventConsumer::name, Function.identity()));
	}

	/** Falhas na fila (esgotaram as tentativas e ainda não foram reprocessadas), das mais antigas. */
	@Transactional(readOnly = true)
	public List<Failure> open() {
		return jdbc.sql("""
				select id, event_id, consumer, envelope ->> 'type' as type, attempts, last_error, first_failed_at, dead_at
				  from events.consumer_failure where dead_at is not null and resolved_at is null
				 order by dead_at, id
				""")
			.query((rs, n) -> new Failure(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
				rs.getString("consumer"), rs.getString("type"), rs.getInt("attempts"), rs.getString("last_error"),
				rs.getObject("first_failed_at", OffsetDateTime.class).toInstant(),
				rs.getObject("dead_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	@Transactional(readOnly = true)
	public int openCount() {
		return jdbc.sql("select count(*) from events.consumer_failure where dead_at is not null and resolved_at is null")
			.query(Integer.class).single();
	}

	/**
	 * Entrega o evento de novo só ao consumidor que falhou. Se passar, a falha sai da fila; se falhar, fica, com o erro
	 * novo. @return vazio se a falha não existe ou já foi resolvida
	 */
	@Transactional(noRollbackFor = RetryFailed.class)
	public Optional<Failure> retry(UUID failureId, UUID by) {
		Optional<Stored> stored = jdbc.sql("""
				select consumer, envelope::text as envelope from events.consumer_failure
				 where id = :id and dead_at is not null and resolved_at is null
				""")
			.param("id", failureId)
			.query((rs, n) -> new Stored(rs.getString("consumer"), rs.getString("envelope")))
			.optional();
		if (stored.isEmpty()) {
			return Optional.empty();
		}
		EventConsumer consumer = consumers.get(stored.get().consumer());
		if (consumer == null) {
			throw new RetryFailed("O consumidor " + stored.get().consumer() + " não existe mais nesta versão.");
		}
		EventEnvelope envelope = json.readValue(stored.get().envelope(), EventEnvelope.class);
		try {
			// Ponto de salvamento: um erro de SQL do consumidor não aborta a transação em que a falha é atualizada.
			savepoint.executeWithoutResult(status -> consumer.accept(envelope));
		} catch (RuntimeException failure) {
			String error = String.valueOf(failure.getMessage());
			jdbc.sql("""
					update events.consumer_failure set attempts = attempts + 1, last_error = :error, last_failed_at = now()
					 where id = :id
					""")
				.param("id", failureId).param("error", error.length() > 1000 ? error.substring(0, 1000) : error).update();
			throw new RetryFailed(error);
		}
		jdbc.sql("update events.consumer_failure set resolved_at = now(), resolved_by = :by where id = :id")
			.param("id", failureId).param("by", by).update();
		return Optional.of(new Failure(failureId, envelope.eventId(), consumer.name(), envelope.type(), 0, null,
			Instant.now(), Instant.now()));
	}

	public record Failure(UUID id, UUID eventId, String consumer, String type, int attempts, String lastError,
			Instant firstFailedAt, Instant deadAt) {
	}

	/** O reprocessamento falhou de novo; a falha continua na fila. */
	public static class RetryFailed extends RuntimeException {
		RetryFailed(String message) {
			super(message);
		}
	}

	private record Stored(String consumer, String envelope) {
	}
}

/**
 * Saúde da entrega à auditoria (#8; REV-03): {@code DELAYED} quando o atraso passa do limite do SLA (5 min). O estado
 * aparece em {@code /actuator/health}, mas não entra nas sondas de prontidão e de vida: a API continua no ar.
 */
@Component("audit")
class AuditHealthIndicator implements HealthIndicator {

	static final Status DELAYED = new Status("DELAYED", "A trilha de auditoria está atrasada.");

	private final OutboxRelay relay;
	private final EventFailures failures;

	AuditHealthIndicator(OutboxRelay relay, EventFailures failures) {
		this.relay = relay;
		this.failures = failures;
	}

	@Override
	public Health health() {
		long lag = relay.lagSeconds();
		Health.Builder builder = lag > relay.lagAlert().toSeconds() ? Health.status(DELAYED) : Health.up();
		return builder.withDetail("lagSeconds", lag).withDetail("alertSeconds", relay.lagAlert().toSeconds())
			.withDetail("deadLetters", failures.openCount()).build();
	}
}
