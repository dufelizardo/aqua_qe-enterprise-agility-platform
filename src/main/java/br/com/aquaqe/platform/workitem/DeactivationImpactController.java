package br.com.aquaqe.platform.workitem;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.workitem.DeactivationImpact.Impact;

/** Aviso antes de desativar uma unidade (#35; SSD 3.2). */
@RestController
class DeactivationImpactController {

	private final DeactivationImpact service;

	DeactivationImpactController(DeactivationImpact service) {
		this.service = service;
	}

	@GetMapping("/api/v1/org-units/{unitId}/deactivation-impact")
	Impact impact(@PathVariable UUID unitId) {
		return service.of(unitId);
	}
}
