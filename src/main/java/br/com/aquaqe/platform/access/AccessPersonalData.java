package br.com.aquaqe.platform.access;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.shared.PersonalData;

/** Papéis da pessoa (#32; REV-14). Na anonimização, os papéis vigentes são revogados; o histórico fica, sem o nome. */
@Component
class AccessPersonalData implements PersonalData {

	private final JdbcClient jdbc;

	AccessPersonalData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "access";
	}

	@Override
	public Object export(UUID userId) {
		return jdbc.sql("""
				select r.key, a.org_unit_id, a.granted_at, a.revoked_at
				  from access.role_assignment a join access.role r on r.id = a.role_id
				 where a.user_id = :id order by a.granted_at, a.id
				""")
			.param("id", userId)
			.query((rs, n) -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("role", rs.getString("key"));
				item.put("orgUnitId", rs.getObject("org_unit_id", UUID.class));
				item.put("grantedAt", rs.getObject("granted_at", OffsetDateTime.class));
				item.put("revokedAt", rs.getObject("revoked_at", OffsetDateTime.class));
				return item;
			})
			.list();
	}

	@Override
	public void anonymize(UUID userId) {
		jdbc.sql("update access.role_assignment set revoked_at = now() where user_id = :id and revoked_at is null")
			.param("id", userId).update();
	}
}
