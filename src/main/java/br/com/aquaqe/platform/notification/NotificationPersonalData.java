package br.com.aquaqe.platform.notification;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.shared.PersonalData;

/** Avisos recebidos pela pessoa (#32; REV-14). A anonimização apaga os avisos dela; os que ela gerou ficam, com o id. */
@Component
class NotificationPersonalData implements PersonalData {

	private final JdbcClient jdbc;

	NotificationPersonalData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "notification";
	}

	@Override
	public Object export(UUID userId) {
		return jdbc.sql("""
				select kind, target_type, target_id, title, excerpt, created_at, read_at
				  from notification.notification where user_id = :id order by created_at desc
				""")
			.param("id", userId)
			.query((rs, n) -> {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("kind", rs.getString("kind"));
				item.put("targetType", rs.getString("target_type"));
				item.put("targetId", rs.getObject("target_id", UUID.class));
				item.put("title", rs.getString("title"));
				item.put("excerpt", rs.getString("excerpt"));
				item.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
				item.put("readAt", rs.getObject("read_at", OffsetDateTime.class));
				return item;
			})
			.list();
	}

	@Override
	public void anonymize(UUID userId) {
		jdbc.sql("delete from notification.notification where user_id = :id").param("id", userId).update();
	}
}
