package br.com.aquaqe.platform.configuration;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.ArtifactType;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.CustomField;
import br.com.aquaqe.platform.configuration.ConfigurationRepository.Workflow;
import br.com.aquaqe.platform.configuration.WorkflowDesigner.UnitWorkflow;
import br.com.aquaqe.platform.shared.ApiException;

/**
 * Configuração (SSD 3.3): tipos de artefato, campos customizados (#17) e workflows, com o Workflow Designer por unidade
 * (#49).
 */
@RestController
@RequestMapping("/api/v1")
class ConfigurationController {

	private final ConfigurationRepository repository;
	private final WorkflowDesigner designer;
	private final CustomFields customFields;

	ConfigurationController(ConfigurationRepository repository, WorkflowDesigner designer, CustomFields customFields) {
		this.repository = repository;
		this.designer = designer;
		this.customFields = customFields;
	}

	@GetMapping("/artifact-types")
	Items<ArtifactType> artifactTypes() {
		return new Items<>(repository.types());
	}

	/** Campos customizados; com {@code artifactType}, só os do tipo (#17). */
	@GetMapping("/custom-fields")
	Items<CustomField> customFields(@RequestParam(required = false) String artifactType) {
		return new Items<>(customFields.list(artifactType));
	}

	@PostMapping("/artifact-types/{key}/custom-fields")
	@ResponseStatus(HttpStatus.CREATED)
	CustomField createCustomField(@PathVariable String key, @RequestBody Map<String, Object> body) {
		return customFields.create(key, body);
	}

	@PatchMapping("/custom-fields/{id}")
	CustomField updateCustomField(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return customFields.update(id, body);
	}

	@DeleteMapping("/custom-fields/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeCustomField(@PathVariable UUID id) {
		customFields.remove(id);
	}

	/** Versões ativas; com {@code orgUnitId}, só as que valem para a unidade ou para as de baixo. */
	@GetMapping("/workflows")
	@Transactional(readOnly = true)
	Items<Workflow> workflows(@RequestParam(required = false) String artifactType,
			@RequestParam(required = false) UUID orgUnitId) {
		return new Items<>(repository.workflows(artifactType, orgUnitId));
	}

	@GetMapping("/workflows/{id}")
	@Transactional(readOnly = true)
	Workflow workflow(@PathVariable UUID id) {
		return repository.workflow(id).orElseThrow(ApiException::notFound);
	}

	@PostMapping("/workflows/{id}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	Workflow publish(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return designer.publish(id, body);
	}

	@DeleteMapping("/workflows/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revert(@PathVariable UUID id) {
		designer.revert(id);
	}

	@GetMapping("/org-units/{unitId}/workflows")
	Items<UnitWorkflow> ofUnit(@PathVariable UUID unitId) {
		return new Items<>(designer.ofUnit(unitId));
	}

	@PostMapping("/org-units/{unitId}/workflows")
	@ResponseStatus(HttpStatus.CREATED)
	Workflow customize(@PathVariable UUID unitId, @RequestBody Map<String, Object> body) {
		return designer.customize(unitId, body);
	}

	record Items<T>(List<T> items) {
	}
}
