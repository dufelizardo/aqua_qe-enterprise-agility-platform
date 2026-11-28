package br.com.aquaqe.platform.events;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * Repassador da outbox (SAD 7.4; REV-03). Entrega os eventos aos consumidores <b>em ordem</b>, um por transação, com um
 * único repassador ativo (trava do PostgreSQL), para manter a cadeia de hash da auditoria.
 * <ul>
 * <li><b>Consumidor crítico</b> (a auditoria): se falha, o repassador <b>para nesse evento e alerta</b> em vez de pular, e
 * tenta de novo na próxima rodada.</li>
 * <li><b>Outros consumidores</b> (#8; D-03): cada um roda num ponto de salvamento. Se falha, o evento é tentado de novo;
 * na {@code max-attempts}-ésima falha, vai para a fila de falhas daquele consumidor ({@code events.consumer_failure}) e a
 * entrega segue. O administrador reprocessa pela API ({@link EventFailures}).</li>
 * </ul>
 * O evento só é marcado como entregue na mesma transação em que os consumidores gravaram, e só os entregues são limpos.
 */
@Component
public class OutboxRelay {

	private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
	/** Chave da trava de transação que garante um único repassador entre instâncias da API. */
	private static final long LOCK_KEY = 0x41515541L;

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;
	private final TransactionTemplate savepoint;
	private final TransactionTemplate separate;
	private final List<EventConsumer> consumers;
	private final ObjectMapper json;
	private final boolean enabled;
	private final Duration lagAlert;
	private final int retentionDays;
	private final int maxAttempts;
	private final AtomicLong lagSeconds = new AtomicLong();

	OutboxRelay(JdbcClient jdbc, PlatformTransactionManager transactionManager, List<EventConsumer> consumers,
			ObjectMapper json, MeterRegistry meters, @Value("${aquaqe.events.relay.enabled:true}") boolean enabled,
			@Value("${aquaqe.events.lag-alert:PT5M}") Duration lagAlert,
			@Value("${aquaqe.events.retention-days:7}") int retentionDays,
			@Value("${aquaqe.events.max-attempts:5}") int maxAttempts) {
		this.jdbc = jdbc;
		this.transactions = new TransactionTemplate(transactionManager);
		this.savepoint = new TransactionTemplate(transactionManager);
		this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
		this.separate = new TransactionTemplate(transactionManager);
		this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.consumers = consumers;
		this.json = json;
		this.enabled = enabled;
		this.lagAlert = lagAlert;
		this.retentionDays = retentionDays;
		this.maxAttempts = maxAttempts;
		Gauge.builder("aquaqe.outbox.lag", lagSeconds, AtomicLong::get)
			.description("Idade, em segundos, do evento mais antigo ainda não entregue")
			.baseUnit("seconds")
			.register(meters);
	}

	@Scheduled(fixedDelayString = "${aquaqe.events.relay.delay:PT1S}")
	void scheduled() {
		if (!enabled) {
			return;
		}
		try {
			drain();
			measureLag();
		} catch (org.springframework.dao.DataAccessException unavailable) {
			// Banco fora do ar: a próxima rodada tenta de novo; o detalhe já aparece nos logs da conexão.
			LOG.warn("Repassador da outbox sem acesso ao banco: {}", unavailable.getMostSpecificCause().getMessage());
		}
	}

	/** Entrega os eventos pendentes, em ordem, até acabar ou até um consumidor falhar. @return quantos entregou */
	public int drain() {
		int delivered = 0;
		while (true) {
			Boolean done;
			try {
				done = transactions.execute(status -> deliverNext());
			} catch (RuntimeException failure) {
				// Parar e alertar (REV-03): pular o evento abriria uma lacuna na cadeia de hash da auditoria.
				LOG.error("Falha ao entregar evento da outbox; o repassador para aqui e tenta de novo depois.", failure);
				return delivered;
			}
			if (!Boolean.TRUE.equals(done)) {
				return delivered;
			}
			delivered++;
		}
	}

	/** Atraso atual da entrega, em segundos (o mesmo da métrica {@code aquaqe.outbox.lag}). */
	public long lagSeconds() {
		return lagSeconds.get();
	}

	/** Limite de atraso a partir do qual a auditoria é dada como atrasada (SLA do REV-03: alvo 1 min, alerta 5 min). */
	public Duration lagAlert() {
		return lagAlert;
	}

	/** @return true se entregou um evento; false se não havia pendente ou outro repassador está ativo */
	private Boolean deliverNext() {
		boolean locked = jdbc.sql("select pg_try_advisory_xact_lock(:key)").param("key", LOCK_KEY)
			.query(Boolean.class).single();
		if (!locked) {
			return false;
		}
		Optional<Pending> next = jdbc.sql("""
				select id, envelope::text as envelope from events.outbox_event
				 where published_at is null order by id limit 1
				""")
			.query((rs, n) -> new Pending(rs.getLong("id"), rs.getString("envelope")))
			.optional();
		if (next.isEmpty()) {
			return false;
		}
		EventEnvelope envelope = json.readValue(next.get().envelope(), EventEnvelope.class);
		for (EventConsumer consumer : consumers) {
			if (consumer.critical()) {
				consumer.accept(envelope);
			} else {
				deliverIsolated(consumer, envelope, next.get().envelope());
			}
		}
		jdbc.sql("update events.outbox_event set published_at = now() where id = :id").param("id", next.get().id())
			.update();
		return true;
	}

	/**
	 * Entrega a um consumidor não crítico num ponto de salvamento: a falha dele não desfaz o que os outros gravaram. Antes
	 * da última tentativa, a falha para o repassador (tenta de novo depois); na última, o evento vai para a fila de falhas.
	 */
	private void deliverIsolated(EventConsumer consumer, EventEnvelope envelope, String rawEnvelope) {
		boolean dead = jdbc.sql("""
				select exists (select 1 from events.consumer_failure
				                where event_id = :event and consumer = :consumer and dead_at is not null)
				""")
			.param("event", envelope.eventId()).param("consumer", consumer.name()).query(Boolean.class).single();
		if (dead) {
			return; // já está na fila de falhas: só o reprocessamento manual tenta de novo
		}
		try {
			savepoint.executeWithoutResult(status -> consumer.accept(envelope));
		} catch (RuntimeException failure) {
			int attempts = recordFailure(consumer.name(), envelope, rawEnvelope, failure);
			if (attempts < maxAttempts) {
				throw failure;
			}
			LOG.error("Evento {} ({}) foi para a fila de falhas do consumidor {} depois de {} tentativas; a entrega segue.",
				envelope.eventId(), envelope.type(), consumer.name(), attempts, failure);
		}
	}

	/** Grava a falha numa transação própria, para sobreviver ao rollback da entrega. @return tentativas até agora */
	private int recordFailure(String consumer, EventEnvelope envelope, String rawEnvelope, RuntimeException failure) {
		String error = String.valueOf(failure.getMessage());
		String message = error.length() > 1000 ? error.substring(0, 1000) : error;
		Integer attempts = separate.execute(status -> jdbc.sql("""
				insert into events.consumer_failure (event_id, consumer, envelope, attempts, last_error)
				values (:event, :consumer, cast(:envelope as jsonb), 1, :error)
				on conflict (event_id, consumer) do update
				   set attempts = events.consumer_failure.attempts + 1, last_error = excluded.last_error,
				       last_failed_at = now()
				returning attempts
				""")
			.param("event", envelope.eventId()).param("consumer", consumer).param("envelope", rawEnvelope)
			.param("error", message)
			.query(Integer.class).single());
		int count = attempts == null ? maxAttempts : attempts;
		if (count >= maxAttempts) {
			separate.executeWithoutResult(status -> jdbc.sql("""
					update events.consumer_failure set dead_at = coalesce(dead_at, now())
					 where event_id = :event and consumer = :consumer
					""")
				.param("event", envelope.eventId()).param("consumer", consumer).update());
		}
		return count;
	}

	/** Atraso da entrega (métrica e alerta no log), comparado ao limite do SLA (REV-03, #8). */
	void measureLag() {
		Optional<OffsetDateTime> oldest = jdbc.sql(
				"select min(occurred_at) from events.outbox_event where published_at is null")
			.query((rs, n) -> rs.getObject(1, OffsetDateTime.class))
			.list().stream().filter(java.util.Objects::nonNull).findFirst();
		long seconds = oldest.map(at -> Duration.between(at.toInstant(), Instant.now()).toSeconds()).orElse(0L);
		lagSeconds.set(seconds);
		if (seconds > lagAlert.toSeconds()) {
			LOG.warn("Outbox com atraso de {} s (limite {} s): a auditoria está atrasada.", seconds, lagAlert.toSeconds());
		}
	}

	/** Limpa os eventos já entregues mais antigos que a retenção (só os entregues: REV-03, recomendação 3). */
	@Scheduled(cron = "${aquaqe.events.cleanup-cron:0 30 3 * * *}")
	void cleanup() {
		if (!enabled) {
			return;
		}
		int removed = jdbc.sql("""
				delete from events.outbox_event
				 where published_at is not null and published_at < now() - make_interval(days => :days)
				""")
			.param("days", retentionDays)
			.update();
		if (removed > 0) {
			LOG.info("Outbox: {} eventos entregues removidos (retenção de {} dias).", removed, retentionDays);
		}
	}

	private record Pending(long id, String envelope) {
	}
}
