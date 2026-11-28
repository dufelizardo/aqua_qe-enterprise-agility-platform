package br.com.aquaqe.platform.privacy;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import br.com.aquaqe.platform.events.OutboxRelay;

/** Dados pessoais (#32; REV-14; SSD 2.2): diretório, exportação e anonimização, só pelo administrador. */
@SpringBootTest(properties = { "aquaqe.access.admin-username=pv.admin", "aquaqe.events.relay.enabled=false" })
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PrivacyIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OutboxRelay relay;

	String portfolio;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("pv.admin"),
				"{\"name\":\"Portfólio PV\",\"level\":\"PORTFOLIO\",\"parentId\":\"" + root + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		grant("ana", "PRODUCT_MANAGER", portfolio);
		grant("leo", "VIEWER", portfolio);
	}

	@Test
	void exportarEAnonimizar() throws Exception {
		String ana = id("ana");
		String admin = id("pv.admin");
		String init = JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("ana"),
				"{\"type\":\"INITIATIVE\",\"title\":\"Privacidade\",\"orgUnitId\":\"" + portfolio
					+ "\",\"ownerId\":\"" + ana + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
		String page = JsonPath.read(mvc.perform(json(post("/api/v1/org-units/{u}/pages", portfolio), user("ana"),
				"{\"title\":\"Página PV\"}")).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id");
		mvc.perform(json(post("/api/v1/pages/{id}/comments", page), user("ana"), "{\"body\":\"Comentário da Ana\"}"))
			.andExpect(status().isCreated());
		mvc.perform(put("/api/v1/me/favorites/pages/{id}", page).with(user("ana")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(json(put("/api/v1/me/preferences"), user("ana"),
				"{\"theme\":\"DARK\",\"notifications\":{\"mention\":true,\"reply\":false}}"))
			.andExpect(status().isOk());

		// Só o administrador vê o diretório, com o e-mail.
		mvc.perform(get("/api/v1/admin/people").param("q", "ana").with(user("leo"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/people").param("q", "ana@").with(user("pv.admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(ana))
			.andExpect(jsonPath("$.items[0].email").value("ana@exemplo.com"))
			.andExpect(jsonPath("$.items[0].anonymized").value(false));

		// Exportar: um arquivo JSON com cada módulo.
		mvc.perform(get("/api/v1/admin/people/{id}/personal-data", ana).with(user("leo"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/admin/people/{id}/personal-data", UUID.randomUUID()).with(user("pv.admin")))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/admin/people/{id}/personal-data", ana).with(user("pv.admin")))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("dados-pessoais-" + ana.substring(0, 8))))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.personId").value(ana))
			.andExpect(jsonPath("$.sections.identity.email").value("ana@exemplo.com"))
			.andExpect(jsonPath("$.sections.identity.preferences.theme").value("DARK"))
			.andExpect(jsonPath("$.sections.access[*].role").value(hasItem("PRODUCT_MANAGER")))
			.andExpect(jsonPath("$.sections.activity.favorites.length()").value(1))
			.andExpect(jsonPath("$.sections.livingdocs.comments[0].body").value("Comentário da Ana"))
			.andExpect(jsonPath("$.sections.workitem[0].key").value(init))
			.andExpect(jsonPath("$.sections.workitem[0].responsibilities[0]").value("OWNER"));

		// Anonimizar: não a própria conta; depois, só uma vez.
		mvc.perform(post("/api/v1/admin/people/{id}/anonymize", admin).with(user("pv.admin")).with(csrf()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("CANNOT_ANONYMIZE_SELF"));
		mvc.perform(post("/api/v1/admin/people/{id}/anonymize", ana).with(user("leo")).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/people/{id}/anonymize", ana).with(user("pv.admin")).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(startsWith("Pessoa removida (")))
			.andExpect(jsonPath("$.anonymized").value(true))
			.andExpect(jsonPath("$.active").value(false));
		mvc.perform(post("/api/v1/admin/people/{id}/anonymize", ana).with(user("pv.admin")).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_ANONYMIZED"));

		// O mesmo login não recria a conta.
		mvc.perform(get("/api/v1/me").with(user("ana")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_ANONYMIZED"));
		assertEquals(1, jdbc.queryForObject("select count(*) from identity.user_account where name = 'ana' or username = 'ana'"
				+ " or id = ?::uuid", Integer.class, ana));

		// O conteúdo fica, com o pseudônimo; o que era só da pessoa sai.
		mvc.perform(get("/api/v1/artifacts/{key}", init).with(user("leo")))
			.andExpect(jsonPath("$.owner.name").value(startsWith("Pessoa removida (")));
		mvc.perform(get("/api/v1/pages/{id}/comments", page).with(user("leo")))
			.andExpect(jsonPath("$.items[0].author.name").value(startsWith("Pessoa removida (")));
		mvc.perform(get("/api/v1/admin/people/{id}/personal-data", ana).with(user("pv.admin")))
			.andExpect(jsonPath("$.sections.identity.email").doesNotExist())
			.andExpect(jsonPath("$.sections.identity.username").doesNotExist())
			.andExpect(jsonPath("$.sections.identity.preferences").doesNotExist())
			.andExpect(jsonPath("$.sections.activity.favorites.length()").value(0))
			.andExpect(jsonPath("$.sections.access[0].revokedAt").exists());

		// A trilha registra as duas ações sem o nome nem o e-mail, e a cadeia continua íntegra.
		relay.drain();
		String trail = mvc.perform(get("/api/v1/audit-events").param("targetId", ana).with(user("pv.admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].action").value(hasItem("person.anonymized")))
			.andExpect(jsonPath("$.items[*].action").value(hasItem("person.data_exported")))
			.andReturn().getResponse().getContentAsString();
		assertFalse(trail.contains("ana@exemplo.com"));
		mvc.perform(get("/api/v1/audit-events/verification").with(user("pv.admin")))
			.andExpect(jsonPath("$.valid").value(true));
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private String id(String username) throws Exception {
		mvc.perform(get("/api/v1/me").with(user(username))).andExpect(status().isOk());
		return jdbc.queryForObject("select id::text from identity.user_account where username = ?", String.class, username);
	}

	private void grant(String username, String role, String orgUnitId) throws Exception {
		mvc.perform(get("/api/v1/me").with(user(username))).andExpect(status().isOk());
		jdbc.update("""
				insert into access.role_assignment (user_id, role_id, org_unit_id)
				select u.id, r.id, ?::uuid from identity.user_account u, access.role r
				 where u.username = ? and r.key = ?
				""", orgUnitId, username, role);
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, RequestPostProcessor user,
			String body) {
		return request.with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static RequestPostProcessor user(String username) {
		return jwt().jwt(token -> token.subject("sub-" + username).claim("preferred_username", username)
			.claim("name", username).claim("email", username + "@exemplo.com"));
	}
}
