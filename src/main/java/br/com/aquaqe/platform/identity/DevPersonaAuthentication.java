package br.com.aquaqe.platform.identity;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import br.com.aquaqe.platform.identity.AuthProperties.DevPersona;

/** Autenticação do modo {@code dev-persona}: a requisição age como a persona escolhida, sem senha (ADR-17). */
final class DevPersonaAuthentication extends AbstractAuthenticationToken {

	private final DevPersona persona;

	DevPersonaAuthentication(DevPersona persona) {
		super(List.of(new SimpleGrantedAuthority("ROLE_" + persona.role())));
		this.persona = persona;
		setAuthenticated(true);
	}

	DevPersona persona() {
		return persona;
	}

	@Override
	public Object getCredentials() {
		return "";
	}

	@Override
	public Object getPrincipal() {
		return persona.username();
	}
}
