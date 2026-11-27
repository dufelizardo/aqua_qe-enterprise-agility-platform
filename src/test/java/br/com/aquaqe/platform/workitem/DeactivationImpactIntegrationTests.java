package br.com.aquaqe.platform.workitem;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/**
 * Aviso antes de desativar uma unidade (#35; SSD 3.2): conta subunidades ativas, artefatos abertos, papéis vigentes e
 * vínculos. A regra é avisar e permitir: a desativação continua aceita com tudo isso.
 */
@SpringBootTest(properties = "aquaqe.access.admin-username=di.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeactivationImpactIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String root;

	@BeforeAll
	void organizacao() throws Exception {
		mvc.perform(get("/api/v1/me").with(user("di.admin"))).andExpect(status().isOk());
		root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
	}

	@Test
	void contaOQueEstaLigadoEADesativacaoContinuaPermitida() throws Exception {
		String portfolio = unit("Portfólio DI", "PORTFOLIO", root);
		String other = unit("Portfólio DI 2", "PORTFOLIO", root);
		String cadeia = unit("Cadeia DI", "CADEIA", portfolio);
		unit("Squad DI", "SQUAD", cadeia);
		String inactive = unit("Cadeia DI desativada", "CADEIA", portfolio);
		mvc.perform(json(patch("/api/v1/org-units/{id}", inactive), user("di.admin"), "{\"active\":false,\"version\":0}"))
			.andExpect(status().isOk());
		mvc.perform(put("/api/v1/org-units/{id}/links/{t}", other, portfolio).with(user("di.admin")).with(csrf()))
			.andExpect(status().is2xxSuccessful());
		grant("pietra", "PRODUCT_MANAGER", portfolio);
		grant("bento", "BUSINESS_OWNER", portfolio);
		mvc.perform(json(post("/api/v1/artifacts"), user("pietra"),
				"{\"type\":\"INITIATIVE\",\"title\":\"Iniciativa DI\",\"orgUnitId\":\"" + portfolio + "\"}"))
			.andExpect(status().isCreated());

		mvc.perform(get("/api/v1/org-units/{id}/deactivation-impact", portfolio).with(user("di.admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activeSubunits").value(2))
			.andExpect(jsonPath("$.openArtifacts").value(1))
			.andExpect(jsonPath("$.activeRoleAssignments").value(2))
			.andExpect(jsonPath("$.links").value(1));
		mvc.perform(get("/api/v1/org-units/{id}/deactivation-impact", other).with(user("di.admin")))
			.andExpect(jsonPath("$.activeSubunits").value(0))
			.andExpect(jsonPath("$.openArtifacts").value(0))
			.andExpect(jsonPath("$.activeRoleAssignments").value(0))
			.andExpect(jsonPath("$.links").value(1));

		mvc.perform(json(patch("/api/v1/org-units/{id}", portfolio), user("di.admin"), "{\"active\":false,\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void soQuemGereConfiguracaoVeOAviso() throws Exception {
		String portfolio = unit("Portfólio DI 3", "PORTFOLIO", root);
		grant("pietra", "PRODUCT_MANAGER", portfolio);
		mvc.perform(get("/api/v1/org-units/{id}/deactivation-impact", portfolio).with(user("pietra")))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/org-units/{id}/deactivation-impact", UUID.randomUUID()).with(user("di.admin")))
			.andExpect(status().isNotFound());
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("di.admin"),
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
