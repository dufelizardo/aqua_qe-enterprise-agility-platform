package br.com.aquaqe.platform.access;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.aquaqe.platform.organization.OrgUnits;

/**
 * Serviço único de decisão de acesso (SAD, seção 7.2). Nesta versão aplica a regra 1 do SSD, seção 2.1: o papel vale na
 * unidade em que foi atribuído e em todas as unidades abaixo dela. As regras de leitura por hierarquia de artefatos
 * (2, 3, 5 e 6) entram com o módulo {@code workitem}.
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
}
