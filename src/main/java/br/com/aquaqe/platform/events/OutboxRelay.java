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
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * Repassador da outbox (SAD 7.4; REV-03). Entrega os eventos aos consumidores <b>em ordem</b>, um por transação, com um
 * único repassador ativo (trava do PostgreSQL), para manter a cadeia de hash da auditoria. Se um consumidor falha, o
 * repassador <b>para nesse evento e alerta</b> em vez de pular; tenta de novo na próxima rodada. O evento só é marcado
 * como entregue na mesma transação em que os consumidores gravaram, e só os entregues são limpos.
 */
@Component
public class OutboxRelay {

	private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
	/** Chave da trava de transação que garante um único repassador entre instâncias da API. */
	private static final long LOCK_KEY = 0x41515541L;

	private final JdbcClient jdbc;
	private final TransactionTemplate transactions;
	private final List<EventConsumer> consumers;
	private final ObjectMapper json;
	private final boolean enabled;
	private final Duration lagAlert;
	private final int retentionDays;
	private final AtomicLong lagSeconds = new AtomicLong();

	OutboxRelay(JdbcClient jdbc, TransactionTemplate transactions, List<EventConsumer> consumers, ObjectMapper json,
			MeterRegistry meters, @Value("${aquaqe.events.relay.enabled:true}") boolean enabled,
			@Value("${aquaqe.events.lag-alert:PT1M}") Duration lagAlert,
			@Value("${aquaqe.events.retention-days:7}") int retentionDays) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.consumers = consumers;
		this.json = json;
		this.enabled = enabled;
		this.lagAlert = lagAlert;
		this.retentionDays = retentionDays;
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
		consumers.forEach(consumer -> consumer.accept(envelope));
		jdbc.sql("update events.outbox_event set published_at = now() where id = :id").param("id", next.get().id())
			.update();
		return true;
	}

	/** Atraso da entrega (métrica e alerta no log). O valor-limite é provisório até o REV-03 definir o SLA. */
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
