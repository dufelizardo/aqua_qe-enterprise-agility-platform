package br.com.aquaqe.platform.workitem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.workitem.DefaultOwners.RuleView;
import br.com.aquaqe.platform.workitem.DefaultOwners.Suggestion;

/** Responsável padrão por unidade e tipo de artefato (#43; SSD 3.4). */
@RestController
@RequestMapping("/api/v1/org-units/{unitId}")
class DefaultOwnerController {

	private final DefaultOwners service;

	DefaultOwnerController(DefaultOwners service) {
		this.service = service;
	}

	@GetMapping("/default-owner-rules")
	Items<RuleView> rules(@PathVariable UUID unitId) {
		return new Items<>(service.rules(unitId));
	}

	@PutMapping("/default-owner-rules/{artifactType}")
	RuleView save(@PathVariable UUID unitId, @PathVariable String artifactType, @RequestBody Map<String, Object> body) {
		return service.save(unitId, artifactType, body);
	}

	@DeleteMapping("/default-owner-rules/{artifactType}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable UUID unitId, @PathVariable String artifactType) {
		service.remove(unitId, artifactType);
	}

	@GetMapping("/default-owner")
	Suggestion suggestion(@PathVariable UUID unitId, @RequestParam String artifactType) {
		return service.suggestion(unitId, artifactType);
	}

	record Items<T>(List<T> items) {
	}
}
