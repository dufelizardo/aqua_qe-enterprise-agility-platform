package br.com.aquaqe.platform.activity;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Home (#83): "Continuar de onde parou" ({@code /me/recent}) e "Novidades" ({@code /me/news}). */
@SpringBootTest(properties = { "aquaqe.access.admin-username=ac.admin", "aquaqe.events.relay.enabled=false" })
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActivityIntegrationTests {

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
	String outro;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio AC", "PORTFOLIO", root);
		outro = unit("Outro Portfólio AC", "PORTFOLIO", root);
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("vitor", "VIEWER", portfolio);
		grant("otavio", "PRODUCT_MANAGER", outro);
	}

	@Test
	void recentesDoMaisNovoParaOMaisAntigoSoOQueAPessoaLe() throws Exception {
		String init = artifact("Recente");
		String page = JsonPath.read(mvc.perform(json(post("/api/v1/org-units/{u}/pages", portfolio), user("paula"),
				"{\"title\":\"Página recente\"}")).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id");
		visit("vitor", "ARTIFACT", init).andExpect(status().isNoContent());
		visit("vitor", "DOCUMENT", init + "/PRD").andExpect(status().isNoContent());
		visit("vitor", "PAGE", page).andExpect(status().isNoContent());
		visit("vitor", "ARTIFACT", init).andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/me/recent").with(user("vitor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].type").value("ARTIFACT"))
			.andExpect(jsonPath("$.items[0].title").value("Recente"))
			.andExpect(jsonPath("$.items[0].orgUnit.name").value("Portfólio AC"))
			.andExpect(jsonPath("$.items[1].type").value("PAGE"))
			.andExpect(jsonPath("$.items[1].title").value("Página recente"))
			.andExpect(jsonPath("$.items[2].documentType").value("PRD"));

		// Quem não lê o item não registra a visita; tipo de documento inválido é 400.
		visit("otavio", "ARTIFACT", init).andExpect(status().isNotFound());
		visit("otavio", "PAGE", page).andExpect(status().isNotFound());
		visit("vitor", "DOCUMENT", init + "/XYZ").andExpect(status().isBadRequest());
		visit("vitor", "OUTRO", init).andExpect(status().isBadRequest());

		// O título mostrado é o atual, não o da hora da visita.
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"title\":\"Renomeado\",\"version\":0}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/me/recent").with(user("vitor")))
			.andExpect(jsonPath("$.items[0].title").value("Renomeado"));
	}

	@Test
	void novidadesDasUnidadesQueAPessoaLeUmaPorItem() throws Exception {
		String init = artifact("Novidade");
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"title\":\"Novidade 2\",\"version\":0}"))
			.andExpect(status().isOk());
		relay.drain();

		mvc.perform(get("/api/v1/me/news").with(user("vitor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].type").value("ARTIFACT"))
			.andExpect(jsonPath("$.items[0].artifactKey").value(init))
			.andExpect(jsonPath("$.items[0].title").value("Novidade 2"))
			.andExpect(jsonPath("$.items[0].action").value("artifact.updated"))
			.andExpect(jsonPath("$.items[0].actor.name").value("paula"))
			.andExpect(jsonPath("$.items[0].own").value(false))
			.andExpect(jsonPath("$.items[?(@.artifactKey == '" + init + "')].action").value(not(hasItem("artifact.created"))));
		mvc.perform(get("/api/v1/me/news").with(user("paula")))
			.andExpect(jsonPath("$.items[0].own").value(true));
		mvc.perform(get("/api/v1/me/news").with(user("otavio")))
			.andExpect(jsonPath("$.items[*].artifactKey").value(not(hasItem(init))));
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private org.springframework.test.web.servlet.ResultActions visit(String username, String type, String ref)
			throws Exception {
		return mvc.perform(json(post("/api/v1/me/recent"), user(username),
				"{\"type\":\"" + type + "\",\"ref\":\"" + ref + "\"}"));
	}

	private String artifact(String title) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("paula"),
				"{\"type\":\"INITIATIVE\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + portfolio + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("ac.admin"),
				"{\"name\":\"" + name + "\",\"level\":\"" + level + "\",\"parentId\":\"" + parentId + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
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
			.claim("name", username));
	}
}
