package br.com.aquaqe.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Sobe a aplicação inteira contra um PostgreSQL real (Testcontainers) e verifica o esqueleto de ponta a ponta. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApplicationIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void healthEhPublico() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void apiSemAutenticacaoResponde401() throws Exception {
		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void navegadorSemSessaoEhLevadoAoLogin() throws Exception {
		mvc.perform(get("/qualquer-pagina").accept(MediaType.TEXT_HTML))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
	}

	@Test
	void loginRedirecionaParaOEnderecoPublicoDoKeycloak() throws Exception {
		mvc.perform(get("/oauth2/authorization/keycloak"))
			.andExpect(status().isFound())
			.andExpect(header().string("Location",
				startsWith("http://localhost:8180/realms/aquaqe-dev/protocol/openid-connect/auth?")))
			.andExpect(header().string("Location", containsString("client_id=aquaqe-web")))
			.andExpect(header().string("Location", containsString("code_challenge=")));
	}

	@Test
	void meDevolveOUsuarioDoToken() throws Exception {
		mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
				.subject("0b9d6c1e-3f52-4c1a-8f0e-5a1b2c3d4e5f")
				.claim("preferred_username", "ana.pm")
				.claim("name", "Ana Silva")
				.claim("email", "ana.silva@aquaqe.local"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("0b9d6c1e-3f52-4c1a-8f0e-5a1b2c3d4e5f"))
			.andExpect(jsonPath("$.username").value("ana.pm"))
			.andExpect(jsonPath("$.name").value("Ana Silva"))
			.andExpect(jsonPath("$.email").value("ana.silva@aquaqe.local"))
			.andExpect(jsonPath("$.assignments").isEmpty());
	}

	@Test
	void logoutDoSpaDevolveOEnderecoDeLogoutDoKeycloak() throws Exception {
		mvc.perform(post("/logout").with(csrf()).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.redirectUrl").value(
				startsWith("http://localhost:8180/realms/aquaqe-dev/protocol/openid-connect/logout?client_id=aquaqe-web")));
	}

	@Test
	void logoutComSessaoOidcLevaOIdTokenAoKeycloak() throws Exception {
		// Sem o id_token_hint, o Keycloak pede confirmação em vez de voltar para a aplicação.
		mvc.perform(post("/logout").with(csrf()).with(oidcLogin().idToken(token -> token.tokenValue("id-token-de-teste")))
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.redirectUrl").value(containsString("id_token_hint=id-token-de-teste")));
	}

	@Test
	void logoutSemTokenCsrfEhRecusado() throws Exception {
		mvc.perform(post("/logout").accept(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden());
	}

	@Test
	void flywayCriaUmSchemaPorModulo() {
		Integer migracoes = jdbc.queryForObject(
			"select count(*) from public.flyway_schema_history where success", Integer.class);
		assertThat(migracoes).as("migrações aplicadas pelo Flyway").isPositive();

		List<String> schemas = jdbc.queryForList("select nspname from pg_namespace", String.class);
		assertThat(schemas).contains("identity", "access", "organization", "configuration", "workitem", "workflow",
			"governance", "livingdocs", "portfolio", "board", "audit", "events");
	}
}
