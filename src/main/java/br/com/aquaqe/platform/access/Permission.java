package br.com.aquaqe.platform.access;

import java.util.Set;

/** Ações da matriz de permissões (SSD, seção 2) e os papéis que as executam. Cresce com os módulos. */
public enum Permission {

	/** "Gerir configuração e papéis". */
	MANAGE_CONFIGURATION(Set.of("PLATFORM_ADMIN"));

	private final Set<String> roles;

	Permission(Set<String> roles) {
		this.roles = roles;
	}

	Set<String> roles() {
		return roles;
	}
}
