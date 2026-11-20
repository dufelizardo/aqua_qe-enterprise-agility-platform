package br.com.aquaqe.platform.board;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.board.SprintService.CloseResult;
import br.com.aquaqe.platform.board.SprintService.Sprint;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;

/** Sprints (SSD 3.9). */
@RestController
@RequestMapping("/api/v1")
class SprintController {

	private final SprintService service;

	SprintController(SprintService service) {
		this.service = service;
	}

	@GetMapping("/org-units/{squadId}/sprints")
	Items<Sprint> sprints(@PathVariable UUID squadId) {
		return new Items<>(service.sprints(squadId));
	}

	@PostMapping("/sprints")
	ResponseEntity<Sprint> create(@RequestBody CreateSprint body) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(service.create(body.squadId(), body.name(), body.goal(), body.startDate(), body.endDate()));
	}

	@PatchMapping("/sprints/{id}")
	Sprint update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.update(id, body);
	}

	@PostMapping("/sprints/{id}/start")
	Sprint start(@PathVariable UUID id) {
		return service.start(id);
	}

	@PostMapping("/sprints/{id}/close")
	CloseResult close(@PathVariable UUID id) {
		return service.close(id);
	}

	@GetMapping("/sprints/{id}/items")
	Items<ArtifactView> items(@PathVariable UUID id) {
		return new Items<>(service.items(id));
	}

	@PutMapping("/sprints/{id}/items/{artifact}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void addItem(@PathVariable UUID id, @PathVariable String artifact) {
		service.addItem(id, artifact);
	}

	@DeleteMapping("/sprints/{id}/items/{artifact}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeItem(@PathVariable UUID id, @PathVariable String artifact) {
		service.removeItem(id, artifact);
	}

	record CreateSprint(UUID squadId, String name, String goal, String startDate, String endDate) {
	}

	record Items<T>(List<T> items) {
	}
}
