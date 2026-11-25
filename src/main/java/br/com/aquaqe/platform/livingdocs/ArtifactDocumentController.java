package br.com.aquaqe.platform.livingdocs;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Documentos da Iniciativa e da Feature: PRD, SAD, SSD, UX e QA (SSD 3.7). */
@RestController
class ArtifactDocumentController {

	private final ArtifactDocumentService service;

	ArtifactDocumentController(ArtifactDocumentService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/artifacts/{artifact}/documents")
	Map<String, Object> list(@PathVariable String artifact) {
		return service.list(artifact);
	}

	@GetMapping("/api/v1/artifacts/{artifact}/documents/{type}")
	Map<String, Object> get(@PathVariable String artifact, @PathVariable String type) {
		return service.get(artifact, type);
	}

	@PutMapping("/api/v1/artifacts/{artifact}/documents/{type}")
	Map<String, Object> save(@PathVariable String artifact, @PathVariable String type,
			@RequestBody Map<String, Object> body) {
		return service.save(artifact, type, body);
	}

	@GetMapping("/api/v1/artifacts/{artifact}/documents/{type}/versions")
	List<Map<String, Object>> versions(@PathVariable String artifact, @PathVariable String type) {
		return service.versions(artifact, type);
	}

	@GetMapping("/api/v1/artifacts/{artifact}/documents/{type}/versions/{number}")
	Map<String, Object> version(@PathVariable String artifact, @PathVariable String type, @PathVariable int number) {
		return service.version(artifact, type, number);
	}

	@PostMapping("/api/v1/artifacts/{artifact}/documents/{type}/status")
	Map<String, Object> changeStatus(@PathVariable String artifact, @PathVariable String type,
			@RequestBody Map<String, Object> body) {
		return service.changeStatus(artifact, type, body);
	}
}
