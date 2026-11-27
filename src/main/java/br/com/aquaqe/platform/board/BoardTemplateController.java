package br.com.aquaqe.platform.board;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.board.BoardTemplates.Template;

/** Catálogo de templates de quadro (#50; SSD 3.9). Salvar um quadro como template fica em {@code /boards/{id}/template}. */
@RestController
@RequestMapping("/api/v1/board-templates")
class BoardTemplateController {

	private final BoardTemplates service;

	BoardTemplateController(BoardTemplates service) {
		this.service = service;
	}

	@GetMapping
	Items<Template> list(@RequestParam(required = false) String level) {
		return new Items<>(service.list(level));
	}

	@PatchMapping("/{id}")
	Template update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.update(id, body);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@PathVariable UUID id) {
		service.remove(id);
	}

	record Items<T>(List<T> items) {
	}
}
