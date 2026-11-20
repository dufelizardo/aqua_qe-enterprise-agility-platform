package br.com.aquaqe.platform.board;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.board.BoardService.Board;
import br.com.aquaqe.platform.board.BoardService.BoardView;

/** Quadros (SSD 3.9). Editar colunas ({@code PATCH}) e sprints entram depois. */
@RestController
@RequestMapping("/api/v1/boards")
class BoardController {

	private final BoardService service;

	BoardController(BoardService service) {
		this.service = service;
	}

	@GetMapping
	Items<Board> boards(@RequestParam(required = false) UUID orgUnitId, @RequestParam(required = false) String level) {
		return new Items<>(service.boards(orgUnitId, level));
	}

	@PostMapping
	ResponseEntity<Board> create(@RequestBody CreateBoard body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body.orgUnitId(), body.level(), body.name()));
	}

	@GetMapping("/{id}")
	Board board(@PathVariable UUID id) {
		return service.board(id);
	}

	@GetMapping("/{id}/view")
	BoardView view(@PathVariable UUID id, @RequestParam(required = false) String type,
			@RequestParam(required = false) UUID assigneeId) {
		return service.view(id, type, assigneeId);
	}

	record CreateBoard(UUID orgUnitId, String level, String name) {
	}

	record Items<T>(List<T> items) {
	}
}
