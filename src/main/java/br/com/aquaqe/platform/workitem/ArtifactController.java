package br.com.aquaqe.platform.workitem;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.shared.PageRequest;
import br.com.aquaqe.platform.shared.PageResponse;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.ArtifactViews.ContextView;

/** Endpoints de artefatos (SSD 3.4). Vínculos, ordenação, RNs e CAs entram nas próximas entregas. */
@RestController
@RequestMapping("/api/v1/artifacts")
class ArtifactController {

	private final ArtifactService service;

	ArtifactController(ArtifactService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<ArtifactView> create(@RequestBody Map<String, Object> body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body));
	}

	@GetMapping
	PageResponse<ArtifactView> list(@RequestHeader(name = "X-Workspace-Id", required = false) UUID workspaceId,
			@RequestParam Map<String, String> query, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return service.list(workspaceId, query, PageRequest.of(page, size));
	}

	@GetMapping("/{artifact}")
	ArtifactView get(@PathVariable String artifact) {
		return service.get(artifact);
	}

	@PatchMapping("/{artifact}")
	ArtifactView update(@PathVariable String artifact, @RequestBody Map<String, Object> body) {
		return service.update(artifact, body);
	}

	@DeleteMapping("/{artifact}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void archive(@PathVariable String artifact) {
		service.archive(artifact);
	}

	@PostMapping("/{artifact}/rank")
	ArtifactView rank(@PathVariable String artifact, @RequestBody RankRequest body) {
		return service.rank(artifact, body.beforeId(), body.afterId());
	}

	record RankRequest(UUID beforeId, UUID afterId) {
	}

	@GetMapping("/{artifact}/children")
	Items<ArtifactView> children(@PathVariable String artifact,
			@RequestParam(defaultValue = "false") boolean archived) {
		return new Items<>(service.children(artifact, archived));
	}

	/** Pessoas que podem ser escolhidas nos campos de pessoa do artefato, filtradas pelo nome ({@code q}). */
	@GetMapping("/{artifact}/people")
	Items<ArtifactViews.Person> people(@PathVariable String artifact, @RequestParam(required = false) String q) {
		return new Items<>(service.people(artifact, q));
	}

	@GetMapping("/{artifact}/context")
	ContextView context(@PathVariable String artifact) {
		return service.context(artifact);
	}

	record Items<T>(List<T> items) {
	}
}
