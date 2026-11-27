package br.com.aquaqe.platform.workitem;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Busca de pessoas para o formulário de item novo, que ainda não tem artefato (#12; REV-07; SSD 3.4). */
@RestController
class UnitPeopleController {

	private final ArtifactService service;

	UnitPeopleController(ArtifactService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/org-units/{unitId}/people")
	Items<ArtifactViews.Person> people(@PathVariable UUID unitId, @RequestParam String artifactType,
			@RequestParam(required = false) String q) {
		return new Items<>(service.peopleForNew(unitId, artifactType, q));
	}

	record Items<T>(List<T> items) {
	}
}
