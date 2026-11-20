package br.com.aquaqe.platform.identity;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.identity.AuthProperties.AuthMode;

/**
 * Trava do modo sem senha (ADR-17): a API não sobe com {@code dev-persona} quando o ambiente é produção, nem sem
 * personas configuradas.
 */
@Component
class AuthModeSafetyCheck implements InitializingBean {

	private final AuthProperties auth;
	private final String environment;

	AuthModeSafetyCheck(AuthProperties auth, @Value("${aquaqe.environment:local}") String environment) {
		this.auth = auth;
		this.environment = environment;
	}

	@Override
	public void afterPropertiesSet() {
		check(auth, environment);
	}

	static void check(AuthProperties auth, String environment) {
		if (auth.mode() != AuthMode.DEV_PERSONA) {
			return;
		}
		if ("prod".equalsIgnoreCase(environment)) {
			throw new IllegalStateException(
				"aquaqe.auth.mode=dev-persona é proibido em produção (aquaqe.environment=prod). Use oidc.");
		}
		if (auth.devPersonas().isEmpty()) {
			throw new IllegalStateException("aquaqe.auth.mode=dev-persona exige ao menos uma persona em aquaqe.auth.dev-personas.");
		}
	}
}
