package br.com.aquaqe.platform.livingdocs;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Páginas de conhecimento por unidade (SSD 3.7.1; Proposta 07, Fase A). */
@RestController
class PageController {

	private final PageService service;

	PageController(PageService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/org-units/{unit}/pages")
	Map<String, Object> list(@PathVariable UUID unit, @RequestParam(defaultValue = "false") boolean archived) {
		return service.list(unit, archived);
	}

	@PostMapping("/api/v1/org-units/{unit}/pages")
	ResponseEntity<Map<String, Object>> create(@PathVariable UUID unit, @RequestBody Map<String, Object> body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(unit, body));
	}

	/** Regimento da unidade, independente do Wiki: um por unidade. */
	@GetMapping("/api/v1/org-units/{unit}/regimento")
	Map<String, Object> regimento(@PathVariable UUID unit) {
		return service.regimento(unit);
	}

	@PostMapping("/api/v1/org-units/{unit}/regimento")
	ResponseEntity<Map<String, Object>> createRegimento(@PathVariable UUID unit,
			@RequestBody(required = false) Map<String, Object> body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.createRegimento(unit, body));
	}

	@GetMapping("/api/v1/pages/{id}")
	Map<String, Object> get(@PathVariable UUID id) {
		return service.get(id);
	}

	@PutMapping("/api/v1/pages/{id}")
	Map<String, Object> save(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.save(id, body);
	}

	@PatchMapping("/api/v1/pages/{id}")
	Map<String, Object> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.update(id, body);
	}

	@GetMapping("/api/v1/pages/{id}/versions")
	Map<String, Object> versions(@PathVariable UUID id) {
		List<Map<String, Object>> items = service.versions(id);
		return Map.of("items", items);
	}

	@GetMapping("/api/v1/pages/{id}/versions/{number}")
	Map<String, Object> version(@PathVariable UUID id, @PathVariable int number) {
		return service.version(id, number);
	}

	@PostMapping("/api/v1/pages/{id}/status")
	Map<String, Object> status(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.changeStatus(id, body);
	}

	@PostMapping("/api/v1/pages/{id}/archive")
	Map<String, Object> archive(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
		return service.archive(id, body);
	}

	@PostMapping("/api/v1/pages/{id}/restore")
	Map<String, Object> restore(@PathVariable UUID id) {
		return service.restore(id);
	}
}
