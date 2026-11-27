package br.com.aquaqe.platform.workitem;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;

/**
 * O que ainda está ligado a uma unidade, para o aviso antes de desativá-la (#35; SSD 3.2). A regra é avisar e permitir:
 * desativar não move nem apaga nada; só tira a unidade das escolhas de pai, de vínculo e de criação.
 */
@Service
class DeactivationImpact {

	private final JdbcClient jdbc;
	private final OrgUnits orgUnits;
	private final Authorization authorization;
	private final CurrentUser currentUser;

	DeactivationImpact(JdbcClient jdbc, OrgUnits orgUnits, Authorization authorization, CurrentUser currentUser) {
		this.jdbc = jdbc;
		this.orgUnits = orgUnits;
		this.authorization = authorization;
		this.currentUser = currentUser;
	}

	/** Só quem gere configuração na unidade, que é quem pode desativá-la. */
	@Transactional(readOnly = true)
	Impact of(UUID unitId) {
		UUID user = currentUser.require().id();
		orgUnits.find(unitId).orElseThrow(ApiException::notFound);
		if (!authorization.can(user, Permission.MANAGE_CONFIGURATION, unitId)) {
			throw ApiException.forbidden();
		}
		int openArtifacts = jdbc.sql("""
				select count(*) from workitem.artifact a join configuration.workflow_status s on s.id = a.status_id
				 where a.org_unit_id = :unit and not a.archived and s.category <> 'DONE'
				""")
			.param("unit", unitId)
			.query(Integer.class)
			.single();
		return new Impact(orgUnits.activeDescendants(unitId), openArtifacts, authorization.activeAssignments(unitId),
			orgUnits.linkCount(unitId));
	}

	/** Contagens do aviso: subunidades ativas (em qualquer profundidade), artefatos abertos, papéis vigentes e vínculos. */
	record Impact(int activeSubunits, int openArtifacts, int activeRoleAssignments, int links) {
	}
}
