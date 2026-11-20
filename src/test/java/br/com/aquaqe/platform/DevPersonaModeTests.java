package br.com.aquaqe.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Modo sem senha do ambiente dev (ADR-17). */
@SpringBootTest(properties = { "aquaqe.auth.mode=dev-persona", "aquaqe.environment=dev" })
@AutoConfigureMockMvc
@Testcontainers
class DevPersonaModeTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Test
	void semLoginAgeComoAPersonaPadrao() throws Exception {
		mvc.perform(get("/api/v1/me"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("robot.pm"))
			.andExpect(jsonPath("$.name").value("Paula Produto"))
			// No modo sem senha, cada persona recebe o seu papel na unidade raiz (SSD 8.5).
			.andExpect(jsonPath("$.assignments[0].role").value("PRODUCT_MANAGER"))
			.andExpect(jsonPath("$.assignments[0].orgUnit.level").value("ENTERPRISE"));
	}

	@Test
	void trocaDePersonaValeParaASessao() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(put("/api/v1/dev/persona").session(session).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"robot.qa\"}"))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/me").session(session))
			.andExpect(jsonPath("$.username").value("robot.qa"))
			.andExpect(jsonPath("$.name").value("Quezia Qualidade"));
		mvc.perform(get("/api/v1/dev/personas").session(session))
			.andExpect(jsonPath("$.current").value("robot.qa"))
			.andExpect(jsonPath("$.personas.length()").value(8));
	}

	@Test
	void personaDesconhecidaEhRecusada() throws Exception {
		mvc.perform(put("/api/v1/dev/persona").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"intruso\"}"))
			.andExpect(status().isBadRequest());
	}
}
