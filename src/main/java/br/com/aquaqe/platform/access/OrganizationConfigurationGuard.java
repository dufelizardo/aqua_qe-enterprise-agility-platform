package br.com.aquaqe.platform.access;

import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.ConfigurationGuard;
import br.com.aquaqe.platform.shared.ApiException;

/** Implementa a verificação "gerir configuração" pedida pelo módulo {@code organization}. */
@Component
class OrganizationConfigurationGuard implements ConfigurationGuard {

	private final CurrentUser currentUser;
	private final Authorization authorization;

	OrganizationConfigurationGuard(CurrentUser currentUser, Authorization authorization) {
		this.currentUser = currentUser;
		this.authorization = authorization;
	}

	@Override
	public UUID requireManage(UUID orgUnitId) {
		UUID userId = currentUser.require().id();
		if (!authorization.can(userId, Permission.MANAGE_CONFIGURATION, orgUnitId)) {
			throw ApiException.forbidden();
		}
		return userId;
	}
}
