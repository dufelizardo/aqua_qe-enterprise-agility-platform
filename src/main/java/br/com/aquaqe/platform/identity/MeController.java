package br.com.aquaqe.platform.identity;

import java.util.List;
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

	record MeResponse(UUID id, String username, String name, String email, List<Assignment> assignments) {
	}
}
