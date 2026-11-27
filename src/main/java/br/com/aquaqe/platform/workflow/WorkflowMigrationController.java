package br.com.aquaqe.platform.workflow;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.workflow.WorkflowMigration.Preview;
import br.com.aquaqe.platform.workflow.WorkflowMigration.Result;

/** Migração explícita dos itens para a versão ativa do workflow (#49; SSD 3.3). */
@RestController
@RequestMapping("/api/v1/workflows/{id}/migration")
class WorkflowMigrationController {

	private final WorkflowMigration service;

	WorkflowMigrationController(WorkflowMigration service) {
		this.service = service;
	}

	@GetMapping
	Preview preview(@PathVariable UUID id) {
		return service.preview(id);
	}

	@PostMapping
	Result migrate(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.migrate(id, body);
	}
}
