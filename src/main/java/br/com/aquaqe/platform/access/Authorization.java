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
	 * O usuário tem o papel informado na unidade ou acima dela? O administrador da plataforma também responde por ele.
	 * Usado para o papel aprovador dos templates de documento (SSD 8.4).
	 */
	public boolean hasRole(UUID userId, String role, UUID orgUnitId) {
		List<UUID> lineage = orgUnits.lineage(orgUnitId);
		return repository.hasAnyRole(userId, Set.of(role, "PLATFORM_ADMIN"), lineage);
	}

	/** Usuários com algum papel vigente em alguma das unidades. */
	public Set<UUID> peopleIn(java.util.Collection<UUID> orgUnitIds) {
		return orgUnitIds.isEmpty() ? Set.of() : Set.copyOf(repository.usersIn(orgUnitIds));
	}

	/**
	 * Unidades cujo conhecimento <b>publicado</b> o usuário lê (SSD 2.1, regra 9; Proposta 07, KNOW-D04): as do
	 * {@link #readScope(UUID) escopo de leitura} e todas as que estão acima das unidades em que ele tem papel.
	 */
	public Set<UUID> knowledgeScope(UUID userId) {
		Set<UUID> scope = new java.util.HashSet<>(readScope(userId));
		repository.assignments(userId).stream()
			.map(AccessRepository.ActiveAssignment::orgUnitId)
			.distinct()
			.forEach(unit -> scope.addAll(orgUnits.lineage(unit)));
		return scope;
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
