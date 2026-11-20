package br.com.aquaqe.platform.access;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.aquaqe.platform.organization.OrgUnits;

/**
 * Serviço único de decisão de acesso (SAD, seção 7.2). Aplica a regra 1 do SSD, seção 2.1: o papel vale na unidade em
 * que foi atribuído e em todas as unidades abaixo dela. As regras que dependem da hierarquia de artefatos (2, 3, 5 e
 * 6) são aplicadas pelo módulo {@code workitem} a partir do {@link #readScope(UUID) escopo de leitura}.
 */
@Service
public class Authorization {

	private final AccessRepository repository;
	private final OrgUnits orgUnits;

	Authorization(AccessRepository repository, OrgUnits orgUnits) {
		this.repository = repository;
		this.orgUnits = orgUnits;
	}

	/** O usuário tem, na unidade ou acima dela, um papel que executa a ação? */
	public boolean can(UUID userId, Permission permission, UUID orgUnitId) {
		List<UUID> lineage = orgUnits.lineage(orgUnitId);
		return repository.hasAnyRole(userId, permission.roles(), lineage);
	}

	/**
	 * Unidades em que o usuário lê tudo (regra 1): as unidades onde tem algum papel vigente e todas as que estão abaixo
	 * delas. Todo papel lê artefatos (matriz do SSD 2).
	 */
	public Set<UUID> readScope(UUID userId) {
		List<UUID> assigned = repository.assignments(userId).stream()
			.map(AccessRepository.ActiveAssignment::orgUnitId)
			.distinct()
			.toList();
		return orgUnits.subtrees(assigned);
	}
}
