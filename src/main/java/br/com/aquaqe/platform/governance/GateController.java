package br.com.aquaqe.platform.governance;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.StatusTransition;
import br.com.aquaqe.platform.governance.GateService.ApprovalView;
import br.com.aquaqe.platform.governance.GateService.GateSummary;
import br.com.aquaqe.platform.governance.GateService.GateView;
import br.com.aquaqe.platform.governance.GateService.PanelGate;
import br.com.aquaqe.platform.workflow.TransitionGuard;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/** Endpoints dos gates (SSD 5 e 3.6; #78). */
@RestController
class GateController {

	private final GateService service;

	GateController(GateService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/gates")
	Items<GateSummary> gates(@RequestParam(required = false) UUID workflowId) {
		return new Items<>(service.list(workflowId));
	}

	@GetMapping("/api/v1/artifacts/{artifact}/approvals")
	Items<ApprovalView> approvals(@PathVariable String artifact) {
		return new Items<>(service.approvals(artifact));
	}

	@GetMapping("/api/v1/workflow-transitions/{id}/gate")
	GateView gate(@PathVariable UUID id) {
		return service.get(id);
	}

	@PutMapping("/api/v1/workflow-transitions/{id}/gate")
	GateView put(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.put(id, body);
	}

	@DeleteMapping("/api/v1/workflow-transitions/{id}/gate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		service.delete(id);
	}

	@GetMapping("/api/v1/artifacts/{artifact}/gates")
	Items<PanelGate> panel(@PathVariable String artifact) {
		return new Items<>(service.panel(artifact));
	}

	@PostMapping("/api/v1/artifacts/{artifact}/approvals")
	Items<PanelGate> decide(@PathVariable String artifact, @RequestBody Map<String, Object> body) {
		return new Items<>(service.decide(artifact, body));
	}

	record Items<T>(List<T> items) {
	}
}

/** O gate como guarda de transição: o {@code workflow} chama antes de mudar o status (SAD 5.1). */
@Component
class GateGuard implements TransitionGuard {

	private final GateService service;

	GateGuard(GateService service) {
		this.service = service;
	}

	@Override
	public List<FailedRule> evaluate(ArtifactRef artifact, StatusTransition transition) {
		return service.evaluate(artifact, transition);
	}
}
