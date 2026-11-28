package br.com.aquaqe.platform.notification;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.events.EventConsumer;
import br.com.aquaqe.platform.events.EventEnvelope;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserPreferences;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.shared.ApiException;

/**
 * Notificações (#85). Consome {@code knowledge_page.commented}: avisa as pessoas mencionadas ({@code MENTION}) e o
 * autor do comentário respondido ({@code REPLY}), nunca quem comentou. Tolera reentrega (ADR-06): um aviso por evento e
 * pessoa. Respeita as preferências da pessoa (#15): o tipo de aviso desligado não é criado.
 */
@Component
class NotificationConsumer implements EventConsumer {

	private final JdbcClient jdbc;
	private final UserPreferences preferences;

	NotificationConsumer(JdbcClient jdbc, UserPreferences preferences) {
		this.jdbc = jdbc;
		this.preferences = preferences;
	}

	@Override
	public void accept(EventEnvelope event) {
		if (!"knowledge_page.commented".equals(event.type()) || event.aggregate() == null) {
			return;
		}
		Map<UUID, String> targets = new LinkedHashMap<>();
		Object mentioned = event.payload().get("mentionedUserIds");
		if (mentioned instanceof List<?> ids) {
			ids.forEach(id -> targets.put(UUID.fromString(String.valueOf(id)), "MENTION"));
		}
		Object replyTo = event.payload().get("replyToUserId");
		if (replyTo != null) {
			targets.putIfAbsent(UUID.fromString(String.valueOf(replyTo)), "REPLY");
		}
		targets.remove(event.actorId());
		// #15: quem desligou o tipo de aviso nas Preferências não recebe.
		targets.entrySet().removeIf(target -> !preferences.notifies(target.getKey(), target.getValue()));
		targets.forEach((user, kind) -> jdbc.sql("""
				insert into notification.notification (event_id, user_id, kind, actor_id, target_type, target_id,
				  org_unit_id, title, excerpt, created_at)
				values (:event, :user, :kind, :actor, 'PAGE', :target, :unit, :title, :excerpt, :at)
				on conflict (event_id, user_id) do nothing
				""")
			.param("event", event.eventId()).param("user", user).param("kind", kind).param("actor", event.actorId())
			.param("target", event.aggregate().id()).param("unit", event.orgUnitId())
			.param("title", limit(event.payload().get("title"), 200))
			.param("excerpt", limit(event.payload().get("excerpt"), 300))
			.param("at", java.sql.Timestamp.from(event.occurredAt()))
			.update());
	}

	private static String limit(Object value, int max) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value);
		return text.length() <= max ? text : text.substring(0, max);
	}
}

/** {@code /me/notifications} (#85): os últimos avisos da pessoa, a contagem dos não lidos e marcar como lido. */
@RestController
@RequestMapping("/api/v1/me/notifications")
class NotificationController {

	static final int LIMIT = 30;

	private final JdbcClient jdbc;
	private final CurrentUser currentUser;
	private final UserDirectory users;

	NotificationController(JdbcClient jdbc, CurrentUser currentUser, UserDirectory users) {
		this.jdbc = jdbc;
		this.currentUser = currentUser;
		this.users = users;
	}

	@GetMapping
	@Transactional(readOnly = true)
	Inbox list() {
		UUID user = currentUser.require().id();
		List<Row> rows = jdbc.sql("""
				select id, kind, actor_id, target_type, target_id, org_unit_id, title, excerpt, created_at, read_at
				  from notification.notification where user_id = :user order by created_at desc limit :limit
				""")
			.param("user", user).param("limit", LIMIT)
			.query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("kind"),
				rs.getObject("actor_id", UUID.class), rs.getString("target_type"), rs.getObject("target_id", UUID.class),
				rs.getObject("org_unit_id", UUID.class), rs.getString("title"), rs.getString("excerpt"),
				rs.getObject("created_at", OffsetDateTime.class).toInstant(),
				rs.getObject("read_at", OffsetDateTime.class) != null))
			.list();
		long unread = jdbc.sql("select count(*) from notification.notification where user_id = :user and read_at is null")
			.param("user", user).query(Long.class).single();
		Map<UUID, UserRef> names = users.find(rows.stream().map(Row::actorId).filter(Objects::nonNull)
			.collect(Collectors.toSet()));
		List<NotificationView> items = rows.stream()
			.map(r -> new NotificationView(r.id(), r.kind(),
				r.actorId() == null ? null
						: new Person(r.actorId(), names.containsKey(r.actorId()) ? names.get(r.actorId()).name() : null),
				r.targetType(), r.targetId(), r.orgUnitId(), r.title(), r.excerpt(), r.createdAt(), r.read()))
			.toList();
		return new Inbox(unread, items);
	}

	@PostMapping("/{id}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void read(@PathVariable UUID id) {
		UUID user = currentUser.require().id();
		int updated = jdbc.sql("""
				update notification.notification set read_at = coalesce(read_at, now()) where id = :id and user_id = :user
				""")
			.param("id", id).param("user", user).update();
		if (updated == 0) {
			throw ApiException.notFound();
		}
	}

	@PostMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void readAll() {
		UUID user = currentUser.require().id();
		jdbc.sql("update notification.notification set read_at = now() where user_id = :user and read_at is null")
			.param("user", user).update();
	}

	private record Row(UUID id, String kind, UUID actorId, String targetType, UUID targetId, UUID orgUnitId,
			String title, String excerpt, Instant createdAt, boolean read) {
	}

	record Person(UUID id, String name) {
	}

	/** Aviso: {@code kind} MENTION ou REPLY; o alvo é a página ({@code targetType} PAGE, {@code targetId}). */
	record NotificationView(UUID id, String kind, Person actor, String targetType, UUID targetId, UUID orgUnitId,
			String title, String excerpt, Instant createdAt, boolean read) {
	}

	record Inbox(long unread, List<NotificationView> items) {
	}
}
