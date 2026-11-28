package br.com.aquaqe.platform.activity;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.shared.PersonalData;

/**
 * O que a pessoa abriu por último e as páginas favoritas (#32; REV-14). São dados de comportamento, sem valor para a
 * auditoria: a anonimização apaga os dois. As novidades das unidades ({@code unit_event}) guardam só o id de quem fez.
 */
@Component
class ActivityPersonalData implements PersonalData {

	private final JdbcClient jdbc;

	ActivityPersonalData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "activity";
	}

	@Override
	public Object export(UUID userId) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("recentVisits", jdbc.sql("""
				select target_type, ref, visited_at from activity.recent_visit where user_id = :id order by visited_at desc
				""")
			.param("id", userId)
			.query((rs, n) -> Map.of("type", rs.getString("target_type"), "ref", rs.getString("ref"), "at",
				rs.getObject("visited_at", OffsetDateTime.class)))
			.list());
		result.put("favorites", jdbc.sql("""
				select target_type, ref, created_at from activity.favorite where user_id = :id order by created_at desc
				""")
			.param("id", userId)
			.query((rs, n) -> Map.of("type", rs.getString("target_type"), "ref", rs.getString("ref"), "at",
				rs.getObject("created_at", OffsetDateTime.class)))
			.list());
		return result;
	}

	@Override
	public void anonymize(UUID userId) {
		jdbc.sql("delete from activity.recent_visit where user_id = :id").param("id", userId).update();
		jdbc.sql("delete from activity.favorite where user_id = :id").param("id", userId).update();
	}
}
