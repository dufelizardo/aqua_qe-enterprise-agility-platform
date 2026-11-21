package br.com.aquaqe.platform.events;

/**
 * Consumidor dos eventos da outbox (SAD 7.4). Implementado por quem consome, como a auditoria; assim {@code events} não
 * depende dos consumidores. A entrega é "pelo menos uma vez" (ADR-06): o consumidor precisa tolerar repetição.
 */
public interface EventConsumer {

	/** Processa o evento. Uma exceção faz o repassador parar nesse evento e tentar de novo depois (REV-03). */
	void accept(EventEnvelope event);
}
