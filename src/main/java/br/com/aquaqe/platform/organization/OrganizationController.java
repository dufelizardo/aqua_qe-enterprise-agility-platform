package br.com.aquaqe.platform.organization;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.organization.OrganizationRepository.Level;
import br.com.aquaqe.platform.organization.OrganizationRepository.Unit;
import br.com.aquaqe.platform.organization.OrganizationRepository.UnitRef;
import br.com.aquaqe.platform.organization.OrganizationService.Links;
import br.com.aquaqe.platform.shared.PageRequest;
import br.com.aquaqe.platform.shared.PageResponse;

/** Endpoints do módulo {@code organization} (SSD, seção 3.2). */
@RestController
@RequestMapping("/api/v1")
class OrganizationController {

	private final OrganizationService service;

	OrganizationController(OrganizationService service) {
		this.service = service;
	}

	@GetMapping("/org-levels")
	Items<LevelView> levels() {
		return new Items<>(service.levels().stream().map(LevelView::of).toList());
	}

	@GetMapping("/org-units")
	PageResponse<UnitView> units(@RequestParam(required = false) UUID parentId,
			@RequestParam(required = false) String level, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		PageResponse<Unit> result = service.units(parentId, level, PageRequest.of(page, size));
		return new PageResponse<>(result.items().stream().map(UnitView::of).toList(), result.page(), result.size(),
			result.totalItems(), result.totalPages());
	}

	@GetMapping("/org-units/{id}")
	UnitDetailView unit(@PathVariable UUID id) {
		return detail(service.unit(id));
	}

	@PostMapping("/org-units")
	ResponseEntity<UnitDetailView> create(@RequestBody CreateUnit body) {
		Unit created = service.create(body.name(), body.level(), body.parentId());
		return ResponseEntity.status(HttpStatus.CREATED).body(detail(created));
	}

	@PatchMapping("/org-units/{id}")
	UnitDetailView update(@PathVariable UUID id, @RequestBody UpdateUnit body) {
		return detail(service.update(id, body.name(), body.active(), body.version()));
	}

	@GetMapping("/org-units/{id}/links")
	LinksView links(@PathVariable UUID id) {
		return LinksView.of(service.links(id));
	}

	@PutMapping("/org-units/{id}/links/{targetId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void link(@PathVariable UUID id, @PathVariable UUID targetId) {
		service.link(id, targetId);
	}

	@DeleteMapping("/org-units/{id}/links/{targetId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unlink(@PathVariable UUID id, @PathVariable UUID targetId) {
		service.unlink(id, targetId);
	}

	private UnitDetailView detail(Unit unit) {
		List<UnitRefView> path = service.pathOf(unit).stream().map(UnitRefView::of).toList();
		return new UnitDetailView(unit.id(), unit.name(), LevelView.of(unit.level()), unit.parentId(), unit.active(),
			unit.version(), path, LinksView.of(service.links(unit.id())));
	}

	record CreateUnit(String name, String level, UUID parentId) {
	}

	record UpdateUnit(String name, Boolean active, Integer version) {
	}

	record Items<T>(List<T> items) {
	}

	record LevelView(String key, String name, int position, String flightLevel) {

		static LevelView of(Level level) {
			return new LevelView(level.key(), level.name(), level.position(), level.flightLevel());
		}
	}

	record UnitRefView(UUID id, String name, String level) {

		static UnitRefView of(UnitRef ref) {
			return new UnitRefView(ref.id(), ref.name(), ref.levelKey());
		}
	}

	record UnitView(UUID id, String name, LevelView level, UUID parentId, boolean active, int version) {

		static UnitView of(Unit unit) {
			return new UnitView(unit.id(), unit.name(), LevelView.of(unit.level()), unit.parentId(), unit.active(),
				unit.version());
		}
	}

	record UnitDetailView(UUID id, String name, LevelView level, UUID parentId, boolean active, int version,
			List<UnitRefView> path, LinksView links) {
	}

	record LinksView(List<UnitRefView> serves, List<UnitRefView> servedBy) {

		static LinksView of(Links links) {
			return new LinksView(links.serves().stream().map(UnitRefView::of).toList(),
				links.servedBy().stream().map(UnitRefView::of).toList());
		}
	}
}
