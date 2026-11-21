package br.com.aquaqe.platform.audit;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.shared.PageRequest;
import br.com.aquaqe.platform.shared.PageResponse;
import br.com.aquaqe.platform.audit.AuditTrail.Verification;
import tools.jackson.databind.ObjectMapper;

/**
 * Consulta da trilha (SSD 3.10): só leitura, para quem pode consultar auditoria (matriz do SSD 2: administrador da
 * plataforma na unidade raiz, porque a trilha cobre a instalação inteira).
 */
@RestController
@RequestMapping("/api/v1/audit-events")
class AuditController {

	private final JdbcClient jdbc;
	private final AuditTrail trail;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final OrgUnits orgUnits;
	private final UserDirectory users;
	private final ObjectMapper json;

	AuditController(JdbcClient jdbc, AuditTrail trail, CurrentUser currentUser, Authorization authorization,
			OrgUnits orgUnits, UserDirectory users, ObjectMapper json) {
		this.jdbc = jdbc;
		this.trail = trail;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.orgUnits = orgUnits;
		this.users = users;
		this.json = json;
	}

	@GetMapping
	PageResponse<AuditView> events(@RequestParam(required = false) String targetType,
			@RequestParam(required = false) UUID targetId, @RequestParam(required = false) UUID actorId,
			@RequestParam(required = false) String action, @RequestParam(required = false) String from,
			@RequestParam(required = false) String to, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		requireAudit();
		PageRequest request = PageRequest.of(page, size);
		String where = """
				 where (cast(:type as varchar) is null or target_type = cast(:type as varchar))
				   and (cast(:target as uuid) is null or target_id = cast(:target as uuid))
				   and (cast(:actor as uuid) is null or actor_id = cast(:actor as uuid))
				   and (cast(:action as varchar) is null or action = cast(:action as varchar))
				   and (cast(:from as timestamptz) is null or occurred_at >= cast(:from as timestamptz))
				   and (cast(:to as timestamptz) is null or occurred_at < cast(:to as timestamptz))
				""";
		Map<String, Object> params = new java.util.HashMap<>();
		params.put("type", targetType);
		params.put("target", targetId);
		params.put("actor", actorId);
		params.put("action", action);
		params.put("from", instant("from", from));
		params.put("to", instant("to", to));
		List<Row> rows = jdbc.sql("""
				select sequence, occurred_at, actor_id, action, target_type, target_id, target_key, before::text as before,
				       after::text as after, correlation_id, hash
				  from audit.audit_event""" + where + " order by sequence desc offset :offset limit :limit")
			.params(params).param("offset", request.offset()).param("limit", request.size())
			.query((rs, n) -> new Row(rs.getLong("sequence"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
				rs.getObject("actor_id", UUID.class), rs.getString("action"), rs.getString("target_type"),
				rs.getObject("target_id", UUID.class), rs.getString("target_key"), rs.getString("before"),
				rs.getString("after"), rs.getString("correlation_id"), rs.getString("hash")))
			.list();
		long total = jdbc.sql("select count(*) from audit.audit_event" + where).params(params).query(Long.class).single();
		Map<UUID, UserRef> names = users.find(rows.stream().map(Row::actorId).filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet()));
		List<AuditView> items = rows.stream()
			.map(r -> new AuditView(r.sequence(), r.occurredAt(),
				r.actorId() == null ? null : new Actor(r.actorId(), names.containsKey(r.actorId()) ? names.get(r.actorId()).name() : null),
				r.action(), new Target(r.targetType(), r.targetId(), r.targetKey()), map(r.before()), map(r.after()),
				r.correlationId(), r.hash()))
			.toList();
		return PageResponse.of(items, request, total);
	}

	@GetMapping("/verification")
	Verification verification() {
		requireAudit();
		return trail.verify();
	}

	private void requireAudit() {
		UUID userId = currentUser.require().id();
		if (!authorization.can(userId, Permission.VIEW_AUDIT, orgUnits.rootId())) {
			throw ApiException.forbidden();
		}
	}

	private static OffsetDateTime instant(String field, String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value);
		} catch (DateTimeParseException e) {
			throw ApiException.validation(field, "use data e hora ISO-8601, ex.: 2026-11-14T13:59:00Z");
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(String value) {
		return value == null ? null : json.readValue(value, Map.class);
	}

	private record Row(long sequence, Instant occurredAt, UUID actorId, String action, String targetType, UUID targetId,
			String targetKey, String before, String after, String correlationId, String hash) {
	}

	record Actor(UUID id, String name) {
	}

	record Target(String type, UUID id, String key) {
	}

	record AuditView(long sequence, Instant occurredAt, Actor actor, String action, Target target,
			Map<String, Object> before, Map<String, Object> after, String correlationId, String hash) {
	}
}
