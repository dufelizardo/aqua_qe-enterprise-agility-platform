package br.com.aquaqe.platform.workflow;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workflow.WorkflowService.HistoryItem;
import br.com.aquaqe.platform.workflow.WorkflowService.TransitionOption;

/** Endpoints do módulo {@code workflow} (SSD 3.5). */
@RestController
@RequestMapping("/api/v1/artifacts/{artifact}")
class WorkflowController {

	private final WorkflowService service;

	WorkflowController(WorkflowService service) {
		this.service = service;
	}

	@GetMapping("/transitions")
	Items<TransitionOption> transitions(@PathVariable String artifact) {
		return new Items<>(service.options(artifact));
	}

	@PostMapping("/transitions")
	ArtifactView execute(@PathVariable String artifact, @RequestBody TransitionRequest body) {
		return service.execute(artifact, body.transitionId(), body.version(), body.comment());
	}

	@GetMapping("/history")
	Items<HistoryItem> history(@PathVariable String artifact) {
		return new Items<>(service.history(artifact));
	}

	record TransitionRequest(UUID transitionId, Integer version, String comment) {
	}

	record Items<T>(List<T> items) {
	}
}
