package br.com.aquaqe.platform.board;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.board.BoardService.Board;
import br.com.aquaqe.platform.board.BoardService.BoardView;
import br.com.aquaqe.platform.board.BoardService.ColumnInput;

/** Quadros (SSD 3.9): criar, configurar colunas e WIP, e a visão com os cartões. Sprints entram depois. */
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

	@PatchMapping("/{id}")
	Board update(@PathVariable UUID id, @RequestBody UpdateBoard body) {
		return service.update(id, body.name(), body.columns());
	}

	@GetMapping("/{id}/view")
	BoardView view(@PathVariable UUID id, @RequestParam(required = false) String type,
			@RequestParam(required = false) UUID assigneeId, @RequestParam(required = false) Boolean blocked,
			@RequestParam(required = false) UUID sprintId) {
		return service.view(id, type, assigneeId, blocked, sprintId);
	}

	record CreateBoard(UUID orgUnitId, String level, String name) {
	}

	record UpdateBoard(String name, List<ColumnInput> columns) {
	}

	record Items<T>(List<T> items) {
	}
}
