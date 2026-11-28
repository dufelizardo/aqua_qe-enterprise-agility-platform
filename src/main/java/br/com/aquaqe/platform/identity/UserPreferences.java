package br.com.aquaqe.platform.identity;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.shared.ApiException;

/**
 * Preferências pessoais (#15; REV-10; UX/UI 7.11, nível Pessoal): o tema e os avisos do sino. Sem preferências
 * gravadas, valem os padrões: tema do sistema e todos os avisos ligados. Outros módulos consultam
 * {@link #notifies(UUID, String)} antes de criar um aviso.
 */
@Service
public class UserPreferences {

	static final Set<String> THEMES = Set.of("SYSTEM", "LIGHT", "DARK");

	private final JdbcClient jdbc;

	UserPreferences(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Preferências da pessoa, ou os padrões se ela nunca gravou. */
	public Preferences of(UUID userId) {
		return jdbc.sql("select theme, notify_mention, notify_reply from identity.user_preference where user_id = :user")
			.param("user", userId)
			.query((rs, n) -> new Preferences(rs.getString("theme"),
				new NotificationPreferences(rs.getBoolean("notify_mention"), rs.getBoolean("notify_reply"))))
			.optional()
			.orElse(Preferences.DEFAULTS);
	}

	/** Se a pessoa quer receber avisos do tipo ({@code MENTION} ou {@code REPLY}); tipos desconhecidos são avisados. */
	public boolean notifies(UUID userId, String kind) {
		NotificationPreferences notifications = of(userId).notifications();
		return switch (kind) {
			case "MENTION" -> notifications.mention();
			case "REPLY" -> notifications.reply();
			default -> true;
		};
	}

	@Transactional
	Preferences save(UUID userId, Preferences preferences) {
		if (preferences == null || preferences.theme() == null || !THEMES.contains(preferences.theme())) {
			throw ApiException.validation("theme", "Use SYSTEM, LIGHT ou DARK.");
		}
		NotificationPreferences notifications = preferences.notifications() == null
				? NotificationPreferences.DEFAULTS : preferences.notifications();
		jdbc.sql("""
				insert into identity.user_preference (user_id, theme, notify_mention, notify_reply, updated_at)
				values (:user, :theme, :mention, :reply, now())
				on conflict (user_id) do update set theme = excluded.theme, notify_mention = excluded.notify_mention,
				  notify_reply = excluded.notify_reply, updated_at = now()
				""")
			.param("user", userId).param("theme", preferences.theme())
			.param("mention", notifications.mention()).param("reply", notifications.reply())
			.update();
		return of(userId);
	}

	public record Preferences(String theme, NotificationPreferences notifications) {
		static final Preferences DEFAULTS = new Preferences("SYSTEM", NotificationPreferences.DEFAULTS);
	}

	public record NotificationPreferences(boolean mention, boolean reply) {
		static final NotificationPreferences DEFAULTS = new NotificationPreferences(true, true);
	}
}

/** {@code GET} e {@code PUT /api/v1/me/preferences} (#15): cada pessoa lê e grava só as próprias preferências. */
@RestController
@RequestMapping("/api/v1/me/preferences")
class PreferencesController {

	private final CurrentUser currentUser;
	private final UserPreferences preferences;

	PreferencesController(CurrentUser currentUser, UserPreferences preferences) {
		this.currentUser = currentUser;
		this.preferences = preferences;
	}

	@GetMapping
	UserPreferences.Preferences get() {
		return preferences.of(currentUser.require().id());
	}

	@PutMapping
	@ResponseStatus(HttpStatus.OK)
	UserPreferences.Preferences put(@RequestBody(required = false) UserPreferences.Preferences body) {
		return preferences.save(currentUser.require().id(), body);
	}
}
