package br.com.aquaqe.platform.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.identity.AssignmentLookup.Assignment;

/** {@code GET /api/v1/me}: usuário corrente e seus papéis (SSD, seção 3.1). */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final CurrentUser currentUser;
	private final AssignmentLookup assignments;

	MeController(CurrentUser currentUser, AssignmentLookup assignments) {
		this.currentUser = currentUser;
		this.assignments = assignments;
	}

	@GetMapping
	MeResponse me() {
		UserAccount account = currentUser.require();
		return new MeResponse(account.id(), account.username(), account.name(), account.email(),
			assignments.assignmentsOf(account.id()));
	}

	/** Unidades em que o usuário tem papel, com os papéis em cada uma: base da seleção de workspace (UX/UI 7.1). */
	@GetMapping("/workspaces")
	Items<Workspace> workspaces() {
		UserAccount account = currentUser.require();
		Map<UUID, Workspace> byUnit = new LinkedHashMap<>();
		for (Assignment assignment : assignments.assignmentsOf(account.id())) {
			if (assignment.orgUnit() == null) {
				continue;
			}
			byUnit.computeIfAbsent(assignment.orgUnit().id(), id -> new Workspace(id, assignment.orgUnit().name(),
				assignment.orgUnit().level(), new ArrayList<>()))
				.roles().add(assignment.role());
		}
		return new Items<>(List.copyOf(byUnit.values()));
	}

	record Workspace(UUID id, String name, String level, List<String> roles) {
	}

	record Items<T>(List<T> items) {
	}

	record MeResponse(UUID id, String username, String name, String email, List<Assignment> assignments) {
	}
}
