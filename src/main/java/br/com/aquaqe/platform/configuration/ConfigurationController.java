package br.com.aquaqe.platform.configuration;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.configuration.ConfigurationRepository.Workflow;
import br.com.aquaqe.platform.shared.ApiException;

/** Leitura da configuração padrão (SSD 3.3). Publicar versões de workflow e template entra depois. */
@RestController
@RequestMapping("/api/v1")
class ConfigurationController {

	private final ConfigurationRepository repository;

	ConfigurationController(ConfigurationRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/artifact-types")
	Items<ArtifactType> artifactTypes() {
		return new Items<>(repository.types());
	}

	@GetMapping("/workflows")
	Items<Workflow> workflows(@RequestParam(required = false) String artifactType) {
		return new Items<>(repository.workflows(artifactType));
	}

	@GetMapping("/workflows/{id}")
	Workflow workflow(@PathVariable UUID id) {
		return repository.workflow(id).orElseThrow(ApiException::notFound);
	}

	record Items<T>(List<T> items) {
	}
}
