package br.com.aquaqe.platform.workitem;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
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

/** Edição dos dados do artefato na página dele: {@code canEdit} e a busca de pessoas (SSD 3.4; UX/UI 7.4 e 7.6). */
@SpringBootTest(properties = "aquaqe.access.admin-username=ed.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtifactEditIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio ED", "PORTFOLIO", root);
		String cadeia = unit("Cadeia ED", "CADEIA", portfolio);
		String squad = unit("Squad ED", "SQUAD", cadeia);
		String outro = unit("Outro Portfólio ED", "PORTFOLIO", root);
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("tiago", "TECH_LEAD", portfolio);
		grant("valeria", "VIEWER", portfolio);
		grant("sabrina", "QA_ENGINEER", squad);
		grant("osvaldo", "PRODUCT_MANAGER", outro);
	}

	@Test
	void quemEditaVeCanEditEOLeitorNao() throws Exception {
		String init = artifact("Editável");
		mvc.perform(get("/api/v1/artifacts/{a}", init).with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canEdit").value(true));
		mvc.perform(get("/api/v1/artifacts/{a}", init).with(user("valeria")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canEdit").value(false));
	}

	@Test
	void pessoasDaLinhagemEDaSubarvoreFiltradasPeloNome() throws Exception {
		String init = artifact("Pessoas");
		mvc.perform(get("/api/v1/artifacts/{a}/people", init).with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(hasItems("paula", "tiago", "valeria", "sabrina")))
			.andExpect(jsonPath("$.items[*].name").value(not(hasItem("osvaldo"))));
		mvc.perform(get("/api/v1/artifacts/{a}/people", init).param("q", "TIA").with(user("paula")))
			.andExpect(jsonPath("$.items[*].name").value(contains("tiago")));
		mvc.perform(get("/api/v1/artifacts/{a}/people", init).param("q", "%").with(user("paula")))
			.andExpect(jsonPath("$.items").isEmpty());
		mvc.perform(get("/api/v1/artifacts/{a}/people", init).with(user("valeria"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/artifacts/{a}/people", init).with(user("osvaldo"))).andExpect(status().isNotFound());
	}

	@Test
	void pessoasParaItemNovoNaUnidade() throws Exception {
		String url = "/api/v1/org-units/{u}/people";
		mvc.perform(get(url, portfolio).param("artifactType", "INITIATIVE").with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(hasItems("paula", "tiago", "valeria", "sabrina")))
			.andExpect(jsonPath("$.items[*].name").value(not(hasItem("osvaldo"))));
		mvc.perform(get(url, portfolio).param("artifactType", "INITIATIVE").param("q", "sab").with(user("paula")))
			.andExpect(jsonPath("$.items[*].name").value(contains("sabrina")));
		mvc.perform(get(url, portfolio).param("artifactType", "INITIATIVE").with(user("valeria")))
			.andExpect(status().isForbidden());
		mvc.perform(get(url, portfolio).param("artifactType", "INITIATIVE").with(user("osvaldo")))
			.andExpect(status().isForbidden());
		mvc.perform(get(url, portfolio).param("artifactType", "NAO_EXISTE").with(user("paula")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("artifactType"));
		mvc.perform(get(url, UUID.randomUUID()).param("artifactType", "INITIATIVE").with(user("paula")))
			.andExpect(status().isNotFound());
	}

	@Test
	void trocarTituloEResponsavelComAVersao() throws Exception {
		String init = artifact("Antes");
		String tiago = jdbc.queryForObject("select id::text from identity.user_account where username = 'tiago'", String.class);
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"),
				"{\"title\":\"Depois\",\"ownerId\":\"" + tiago + "\",\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Depois"))
			.andExpect(jsonPath("$.owner.name").value("tiago"))
			.andExpect(jsonPath("$.canEdit").value(true));
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"title\":\"Velho\",\"version\":0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private String artifact(String title) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("paula"),
				"{\"type\":\"INITIATIVE\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + portfolio + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("ed.admin"),
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
