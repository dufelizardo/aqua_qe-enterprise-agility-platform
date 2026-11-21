package br.com.aquaqe.platform.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope de um evento de domínio (SSD 7.1). {@code before} e {@code after} levam os valores anterior e posterior que
 * a auditoria registra; {@code payload} traz os campos do catálogo (SSD 7.2).
 */
public record EventEnvelope(UUID eventId, String type, Instant occurredAt, UUID actorId, UUID orgUnitId,
		String correlationId, Aggregate aggregate, Map<String, Object> payload, Map<String, Object> before,
		Map<String, Object> after) {

	/** O recurso afetado: tipo ({@code ARTIFACT}, {@code ORG_UNIT}...), id e, quando houver, a chave legível. */
	public record Aggregate(String type, UUID id, String key) {
	}
}
