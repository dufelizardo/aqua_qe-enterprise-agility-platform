package br.com.aquaqe.platform.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.aquaqe.platform.identity.AuthProperties.AuthMode;
import br.com.aquaqe.platform.identity.AuthProperties.DevPersona;

/** A trava do ADR-17: o modo sem senha nunca sobe em produção. */
class AuthModeSafetyCheckTests {

	private static final List<DevPersona> PERSONAS =
		List.of(new DevPersona("robot.pm", "Paula Produto", "robot.pm@aquaqe.local", "PRODUCT_MANAGER"));

	private static AuthProperties props(AuthMode mode, List<DevPersona> personas) {
		return new AuthProperties("http://kc", "http://kc", "aquaqe-dev", "aquaqe-web", mode, personas);
	}

	@Test
	void modoSemSenhaEmProducaoImpedeASubida() {
		assertThatThrownBy(() -> AuthModeSafetyCheck.check(props(AuthMode.DEV_PERSONA, PERSONAS), "prod"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("proibido em produção");
	}

	@Test
	void modoSemSenhaSemPersonasImpedeASubida() {
		assertThatThrownBy(() -> AuthModeSafetyCheck.check(props(AuthMode.DEV_PERSONA, List.of()), "dev"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void modoSemSenhaEmDevSobe() {
		assertThatCode(() -> AuthModeSafetyCheck.check(props(AuthMode.DEV_PERSONA, PERSONAS), "dev")).doesNotThrowAnyException();
	}

	@Test
	void loginRealEmProducaoSobe() {
		assertThatCode(() -> AuthModeSafetyCheck.check(props(AuthMode.OIDC, List.of()), "prod")).doesNotThrowAnyException();
	}
}
