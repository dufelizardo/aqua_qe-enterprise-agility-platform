package br.com.aquaqe.platform.activity;

import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.events.EventConsumer;
import br.com.aquaqe.platform.events.EventEnvelope;

/**
 * Consumidor da outbox (SAD 7.4) que guarda, por unidade, os eventos de artefatos, documentos e páginas do Wiki: a base
 * das "Novidades" da Home (#83). Tolera reentrega (ADR-06): o mesmo {@code eventId} não grava duas linhas.
 */
@Component
class UnitActivity implements EventConsumer {

	static final Set<String> ARTIFACT_ACTIONS = Set.of("artifact.created", "artifact.updated", "artifact.status_changed",
		"artifact.archived");
	static final Set<String> TARGETS = Set.of("ARTIFACT", "DOCUMENT", "KNOWLEDGE_PAGE");

	private final JdbcClient jdbc;

	UnitActivity(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void accept(EventEnvelope event) {
		if (!relevant(event)) {
			return;
		}
		jdbc.sql("""
				insert into activity.unit_event (event_id, occurred_at, org_unit_id, actor_id, action, target_type, target_id,
				  target_key)
				values (:event, :at, :unit, :actor, :action, :type, :target, :key)
				on conflict (event_id) do nothing
				""")
			.param("event", event.eventId()).param("at", java.sql.Timestamp.from(event.occurredAt()))
			.param("unit", event.orgUnitId()).param("actor", event.actorId()).param("action", event.type())
			.param("type", event.aggregate().type()).param("target", event.aggregate().id())
			.param("key", truncate(event.aggregate().key()))
			.update();
	}

	static boolean relevant(EventEnvelope event) {
		if (event.orgUnitId() == null || event.aggregate() == null || event.aggregate().id() == null
				|| !TARGETS.contains(event.aggregate().type())) {
			return false;
		}
		return !"ARTIFACT".equals(event.aggregate().type()) || ARTIFACT_ACTIONS.contains(event.type());
	}

	private static String truncate(String key) {
		return key == null || key.length() <= 200 ? key : key.substring(0, 200);
	}
}
