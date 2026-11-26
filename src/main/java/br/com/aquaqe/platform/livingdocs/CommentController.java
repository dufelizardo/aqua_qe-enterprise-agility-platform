package br.com.aquaqe.platform.livingdocs;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.livingdocs.CommentService.CommentView;

/** Comentários das páginas e pessoas que podem ser mencionadas (SSD 3.7.1; #85). */
@RestController
@RequestMapping("/api/v1/pages/{id}")
class CommentController {

	private final CommentService service;

	CommentController(CommentService service) {
		this.service = service;
	}

	@GetMapping("/comments")
	Items<CommentView> list(@PathVariable UUID id) {
		return new Items<>(service.list(id));
	}

	@PostMapping("/comments")
	@ResponseStatus(HttpStatus.CREATED)
	CommentView create(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
		return service.create(id, body);
	}

	@DeleteMapping("/comments/{comment}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id, @PathVariable UUID comment) {
		service.delete(id, comment);
	}

	@GetMapping("/people")
	Items<Person> people(@PathVariable UUID id, @RequestParam(required = false) String q) {
		return new Items<>(service.people(id, q).stream().map(u -> new Person(u.id(), u.name())).toList());
	}

	record Person(UUID id, String name) {
		static Person of(UserRef user) {
			return new Person(user.id(), user.name());
		}
	}

	record Items<T>(List<T> items) {
	}
}
