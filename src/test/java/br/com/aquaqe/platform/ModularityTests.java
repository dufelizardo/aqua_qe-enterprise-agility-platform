package br.com.aquaqe.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fronteiras do monólito modular (ADR-04): falha o build se um módulo usar classes internas de outro ou se houver
 * dependência circular entre módulos.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(AquaQeApplication.class);

	@Test
	void modulosRespeitamAsFronteiras() {
		modules.verify();
	}
}
