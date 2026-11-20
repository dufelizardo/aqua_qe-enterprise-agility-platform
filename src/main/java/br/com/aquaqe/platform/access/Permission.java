package br.com.aquaqe.platform.access;

import java.util.Set;

/** Ações da matriz de permissões (SSD, seção 2) e os papéis que as executam. Cresce com os módulos. */
public enum Permission {

	/** "Gerir configuração e papéis". */
	MANAGE_CONFIGURATION(Set.of("PLATFORM_ADMIN")),

	/** "Criar e editar Iniciativa" (FL3). */
	EDIT_FL3(Set.of("PLATFORM_ADMIN", "BUSINESS_OWNER", "PRODUCT_MANAGER")),

	/** "Criar e editar Feature" (FL2). */
	EDIT_FL2(Set.of("PLATFORM_ADMIN", "PRODUCT_MANAGER", "TECH_LEAD")),

	/** "Criar e editar itens de FL1". */
	EDIT_FL1(Set.of("PLATFORM_ADMIN", "PRODUCT_MANAGER", "TECH_LEAD", "UX_DESIGNER", "QA_ENGINEER", "SQUAD_MEMBER"));

	private final Set<String> roles;

	Permission(Set<String> roles) {
		this.roles = roles;
	}

	Set<String> roles() {
		return roles;
	}

	/** Permissão de criar e editar artefatos do Flight Level informado ({@code FL1}, {@code FL2} ou {@code FL3}). */
	public static Permission editAt(String flightLevel) {
		return switch (flightLevel) {
			case "FL3" -> EDIT_FL3;
			case "FL2" -> EDIT_FL2;
			case "FL1" -> EDIT_FL1;
			default -> throw new IllegalArgumentException("Flight Level desconhecido: " + flightLevel);
		};
	}
}
