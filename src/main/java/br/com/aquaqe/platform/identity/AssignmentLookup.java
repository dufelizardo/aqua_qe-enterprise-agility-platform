package br.com.aquaqe.platform.identity;

import java.util.List;
import java.util.UUID;

/**
 * Papéis do usuário, para o {@code GET /me}. Implementada pelo módulo {@code access}, que depende deste; assim
 * {@code identity} não depende de {@code access}.
 */
public interface AssignmentLookup {

	List<Assignment> assignmentsOf(UUID userId);

	record Assignment(UUID id, String role, OrgUnit orgUnit) {
	}

	record OrgUnit(UUID id, String name, String level) {
	}
}
