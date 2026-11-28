package br.com.aquaqe.platform.events;

/**
 * Consumidor dos eventos da outbox (SAD 7.4). Implementado por quem consome, como a auditoria; assim {@code events} não
 * depende dos consumidores. A entrega é "pelo menos uma vez" (ADR-06): o consumidor precisa tolerar repetição.
 */
public interface EventConsumer {

	/**
	 * Processa o evento. Se um consumidor <b>crítico</b> falha, o repassador para nesse evento e tenta de novo depois
	 * (REV-03). Se outro consumidor falha, ele é tentado de novo e, depois de algumas tentativas, o evento vai para a fila
	 * de falhas desse consumidor, sem segurar os demais (#8; D-03).
	 */
	void accept(EventEnvelope event);

	/** Crítico é o consumidor cuja lacuna não se aceita: a trilha de auditoria. Os outros vão para a fila de falhas. */
	default boolean critical() {
		return false;
	}

	/** Nome estável do consumidor, gravado na fila de falhas. */
	default String name() {
		return getClass().getSimpleName();
	}
}
