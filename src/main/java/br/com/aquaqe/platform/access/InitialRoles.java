package br.com.aquaqe.platform.access;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.identity.AuthProperties;
import br.com.aquaqe.platform.identity.AuthProperties.AuthMode;
import br.com.aquaqe.platform.identity.UserAccountCreated;
import br.com.aquaqe.platform.organization.OrgUnits;

/**
 * Papéis iniciais na unidade raiz (SSD, seção 8.5), dados quando a conta é criada: o administrador indicado na
 * instalação e, fora de produção, o papel de cada persona de teste.
 */
@Component
class InitialRoles implements InitializingBean {

	private final AccessProperties properties;
	private final AuthProperties auth;
	private final AccessRepository repository;
	private final OrgUnits orgUnits;
	private final String environment;

	InitialRoles(AccessProperties properties, AuthProperties auth, AccessRepository repository, OrgUnits orgUnits,
			@Value("${aquaqe.environment:local}") String environment) {
		this.properties = properties;
		this.auth = auth;
		this.repository = repository;
		this.orgUnits = orgUnits;
		this.environment = environment;
	}

	/** Trava: papéis de persona dão poderes a usuários de teste conhecidos; nunca em produção. */
	@Override
	public void afterPropertiesSet() {
		if (properties.personaRoles() && "prod".equalsIgnoreCase(environment)) {
			throw new IllegalStateException(
				"aquaqe.access.persona-roles=true é proibido em produção (aquaqe.environment=prod).");
		}
	}

	@EventListener
	void on(UserAccountCreated event) {
		String username = event.account().username();
		if (username == null) {
			return;
		}
		if (username.equals(properties.adminUsername())) {
			repository.grant(event.account().id(), "PLATFORM_ADMIN", orgUnits.rootId(), null);
		}
		if (personaRolesEnabled()) {
			auth.devPersonas().stream()
				.filter(p -> p.username().equals(username))
				.findFirst()
				.ifPresent(p -> repository.grant(event.account().id(), p.role(), orgUnits.rootId(), null));
		}
	}

	private boolean personaRolesEnabled() {
		return properties.personaRoles() || auth.mode() == AuthMode.DEV_PERSONA;
	}
}
