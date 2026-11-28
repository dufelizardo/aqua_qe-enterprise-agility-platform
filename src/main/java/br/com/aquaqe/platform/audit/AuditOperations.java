package br.com.aquaqe.platform.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.audit.AuditTrail.Anchor;
import br.com.aquaqe.platform.events.EventFailures;
import br.com.aquaqe.platform.events.EventFailures.Failure;
import br.com.aquaqe.platform.events.OutboxRelay;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;

/** Âncora periódica da cadeia de hash (#8; REV-03): só grava quando a trilha cresceu desde a última. */
@Component
class AuditAnchors {

	private final AuditTrail trail;
	private final boolean enabled;

	AuditAnchors(AuditTrail trail, @Value("${aquaqe.audit.anchor.enabled:true}") boolean enabled) {
		this.trail = trail;
		this.enabled = enabled;
	}

	@Scheduled(fixedDelayString = "${aquaqe.audit.anchor.interval:PT1H}",
		initialDelayString = "${aquaqe.audit.anchor.interval:PT1H}")
	@Transactional
	void scheduled() {
		if (enabled) {
			trail.anchor();
		}
	}
}

/**
 * Operação da auditoria (#8; REV-03; SSD 3.10): situação da entrega (atraso e SLA), âncoras da cadeia e a fila de falhas
 * dos outros consumidores. Só o administrador da plataforma na unidade raiz, como a consulta da trilha.
 */
@RestController
class AuditOperationsController {

	/** Alvo do SLA: o evento entra na trilha em até 1 minuto em operação normal. */
	static final long TARGET_SECONDS = 60;

	private final AuditTrail trail;
	private final OutboxRelay relay;
	private final EventFailures failures;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final OrgUnits orgUnits;

	AuditOperationsController(AuditTrail trail, OutboxRelay relay, EventFailures failures, CurrentUser currentUser,
			Authorization authorization, OrgUnits orgUnits) {
		this.trail = trail;
		this.relay = relay;
		this.failures = failures;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.orgUnits = orgUnits;
	}

	@GetMapping("/api/v1/audit-events/status")
	Map<String, Object> status() {
		requireAudit();
		long lag = relay.lagSeconds();
		long alert = relay.lagAlert().toSeconds();
		List<Anchor> anchors = trail.anchors();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("lagSeconds", lag);
		result.put("targetSeconds", TARGET_SECONDS);
		result.put("alertSeconds", alert);
		result.put("delayed", lag > alert);
		result.put("deadLetters", failures.openCount());
		result.put("lastAnchor", anchors.isEmpty() ? null : anchors.getLast());
		return result;
	}

	@GetMapping("/api/v1/audit-events/anchors")
	Map<String, Object> anchors() {
		requireAudit();
		return Map.of("items", trail.anchors());
	}

	@GetMapping("/api/v1/admin/event-failures")
	Map<String, Object> failures() {
		requireAudit();
		return Map.of("items", failures.open());
	}

	@PostMapping("/api/v1/admin/event-failures/{id}/retry")
	Failure retry(@PathVariable UUID id) {
		UUID admin = requireAudit();
		try {
			return failures.retry(id, admin).orElseThrow(ApiException::notFound);
		} catch (EventFailures.RetryFailed failed) {
			throw new ApiException(HttpStatus.CONFLICT, "RETRY_FAILED",
				"O reprocessamento falhou de novo; o evento continua na fila. " + failed.getMessage());
		}
	}

	private UUID requireAudit() {
		UUID userId = currentUser.require().id();
		if (!authorization.can(userId, Permission.VIEW_AUDIT, orgUnits.rootId())) {
			throw ApiException.forbidden();
		}
		return userId;
	}
}
