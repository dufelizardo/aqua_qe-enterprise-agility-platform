package br.com.aquaqe.platform.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.shared.PersonalData;

/**
 * Conta e preferências (#32; REV-14). Na anonimização, o nome vira "Pessoa removida (XXXX)", usuário e e-mail são
 * apagados, a conta fica inativa e o identificador do IdP vira um hash: o mesmo login não recria a conta nem volta a ligar
 * a pessoa ao id ({@link CurrentUser}).
 */
@Component
class IdentityPersonalData implements PersonalData {

	static final String ANONYMIZED_PREFIX = "anonimizado:";

	private final JdbcClient jdbc;

	IdentityPersonalData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "identity";
	}

	@Override
	public Object export(UUID userId) {
		Map<String, Object> result = new LinkedHashMap<>();
		jdbc.sql("""
				select username, name, email, active, created_at, last_seen_at from identity.user_account where id = :id
				""")
			.param("id", userId)
			.query(rs -> {
				result.put("username", rs.getString("username"));
				result.put("name", rs.getString("name"));
				result.put("email", rs.getString("email"));
				result.put("active", rs.getBoolean("active"));
				result.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
				result.put("lastSeenAt", rs.getObject("last_seen_at", OffsetDateTime.class));
			});
		jdbc.sql("select theme, notify_mention, notify_reply from identity.user_preference where user_id = :id")
			.param("id", userId)
			.query(rs -> {
				result.put("preferences", Map.of("theme", rs.getString("theme"), "notifyMention",
					rs.getBoolean("notify_mention"), "notifyReply", rs.getBoolean("notify_reply")));
			});
		return result;
	}

	@Override
	public void anonymize(UUID userId) {
		jdbc.sql("delete from identity.user_preference where user_id = :id").param("id", userId).update();
		String subject = jdbc.sql("select subject from identity.user_account where id = :id")
			.param("id", userId).query(String.class).single();
		jdbc.sql("""
				update identity.user_account
				   set subject = :subject, username = null, email = null, name = :name, active = false
				 where id = :id
				""")
			.param("id", userId)
			.param("subject", anonymizedSubject(subject))
			.param("name", pseudonym(userId))
			.update();
	}

	@Override
	public int order() {
		return 100;
	}

	/** "Pessoa removida (A1B2)": as quatro primeiras letras do id, para distinguir duas pessoas removidas na mesma tela. */
	static String pseudonym(UUID userId) {
		return "Pessoa removida (" + userId.toString().substring(0, 4).toUpperCase() + ")";
	}

	/** O identificador do IdP vira hash: não identifica, mas reconhece o mesmo login. */
	static String anonymizedSubject(String subject) {
		if (subject.startsWith(ANONYMIZED_PREFIX)) {
			return subject;
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8));
			return ANONYMIZED_PREFIX + HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
