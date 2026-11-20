package br.com.aquaqe.platform.access;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.identity.AssignmentLookup;
import br.com.aquaqe.platform.organization.OrgUnits;

/** Fornece ao {@code GET /me} os papéis vigentes do usuário, com a unidade de cada um. */
@Component
class RoleAssignmentLookup implements AssignmentLookup {

	private final AccessRepository repository;
	private final OrgUnits orgUnits;

	RoleAssignmentLookup(AccessRepository repository, OrgUnits orgUnits) {
		this.repository = repository;
		this.orgUnits = orgUnits;
	}

	@Override
	public List<Assignment> assignmentsOf(UUID userId) {
		return repository.assignments(userId).stream()
			.map(a -> new Assignment(a.id(), a.role(), orgUnits.find(a.orgUnitId())
				.map(u -> new OrgUnit(u.id(), u.name(), u.levelKey()))
				.orElse(null)))
			.toList();
	}
}
