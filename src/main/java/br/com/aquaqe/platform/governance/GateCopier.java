package br.com.aquaqe.platform.governance;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.configuration.WorkflowVersionPublished;

/**
 * Ao publicar uma versão de workflow (ou copiá-lo para uma unidade), os gates vão junto: cada transição equivalente da
 * nova versão recebe o gate e as regras da transição de origem (#49). A versão de origem mantém os dela, porque os
 * artefatos que ficaram nela continuam usando-os. As aprovações não são copiadas: valem no status em que foram dadas.
 */
@Component
class GateCopier {

	private final JdbcClient jdbc;

	GateCopier(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@EventListener
	void on(WorkflowVersionPublished event) {
		for (Map.Entry<UUID, UUID> pair : event.transitions().entrySet()) {
			UUID gateId = UUID.randomUUID();
			int copied = jdbc.sql("""
					insert into governance.gate (id, transition_id, name, updated_by)
					select :id, :to, name, :user from governance.gate where transition_id = :from
					""")
				.param("id", gateId).param("to", pair.getValue()).param("from", pair.getKey())
				.param("user", event.actorId())
				.update();
			if (copied == 1) {
				jdbc.sql("""
						insert into governance.gate_rule (gate_id, position, type, params, message)
						select :gate, r.position, r.type, r.params, r.message
						  from governance.gate_rule r join governance.gate g on g.id = r.gate_id
						 where g.transition_id = :from
						""")
					.param("gate", gateId).param("from", pair.getKey())
					.update();
			}
		}
	}
}
