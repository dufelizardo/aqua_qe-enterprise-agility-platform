package br.com.aquaqe.platform.workitem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.shared.PersonalData;

/**
 * Artefatos em que a pessoa é Owner, Technical Lead ou QA (#32; REV-14). A responsabilidade é dado do artefato: a
 * anonimização não a apaga, e o item passa a mostrar o pseudônimo até alguém trocar o responsável.
 */
@Component
class WorkitemPersonalData implements PersonalData {

	private final JdbcClient jdbc;

	WorkitemPersonalData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "workitem";
	}

	@Override
	public Object export(UUID userId) {
		return jdbc.sql("""
				select key, title, owner_id = :id as owner, technical_lead_id = :id as tech_lead, qa_owner_id = :id as qa
				  from workitem.artifact
				 where owner_id = :id or technical_lead_id = :id or qa_owner_id = :id
				 order by key
				""")
			.param("id", userId)
			.query((rs, n) -> {
				List<String> roles = new ArrayList<>();
				if (rs.getBoolean("owner")) {
					roles.add("OWNER");
				}
				if (rs.getBoolean("tech_lead")) {
					roles.add("TECHNICAL_LEAD");
				}
				if (rs.getBoolean("qa")) {
					roles.add("QA_OWNER");
				}
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("key", rs.getString("key"));
				item.put("title", rs.getString("title"));
				item.put("responsibilities", roles);
				return item;
			})
			.list();
	}

	@Override
	public void anonymize(UUID userId) {
		// Dado do artefato: o responsável continua o mesmo id, agora com o pseudônimo.
	}
}
