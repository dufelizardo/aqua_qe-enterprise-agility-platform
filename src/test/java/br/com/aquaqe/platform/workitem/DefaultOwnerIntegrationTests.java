package br.com.aquaqe.platform.workitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/**
 * Responsável padrão do artefato criado sem Owner (#43): regra por unidade e tipo, herdada da unidade acima; quem
 * criou, ou quem tem o papel na unidade (ou na acima mais próxima); empate fica para a escolha no formulário.
 */
@SpringBootTest(properties = "aquaqe.access.admin-username=do.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DefaultOwnerIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String root;
	String portfolio;
	String otherPortfolio;

	@BeforeAll
	void organizacao() throws Exception {
		mvc.perform(get("/api/v1/me").with(user("do.admin"))).andExpect(status().isOk());
		root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio DO", root);
		otherPortfolio = unit("Portfólio DO 2", root);
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("paula", "PRODUCT_MANAGER", otherPortfolio);
		grant("bruno", "BUSINESS_OWNER", portfolio);
		grant("quintino", "QA_ENGINEER", root);
	}

	@Test
	void semRegraNasceSemOwnerECriadorVemDaRaizPorHeranca() throws Exception {
		create(otherPortfolio, null).andExpect(jsonPath("$.owner").doesNotExist());

		rule(root, "INITIATIVE", "{\"mode\":\"CREATOR\"}", "do.admin").andExpect(status().isOk())
			.andExpect(jsonPath("$.inherited").value(false));
		mvc.perform(get("/api/v1/org-units/{u}/default-owner-rules", otherPortfolio).with(user("paula")))
			.andExpect(jsonPath("$.items[?(@.artifactType.key == 'INITIATIVE')].mode").value(org.hamcrest.Matchers.contains("CREATOR")))
			.andExpect(jsonPath("$.items[?(@.artifactType.key == 'INITIATIVE')].inherited").value(org.hamcrest.Matchers.contains(true)))
			.andExpect(jsonPath("$.items[?(@.artifactType.key == 'INITIATIVE')].source.id").value(org.hamcrest.Matchers.contains(root)));
		create(otherPortfolio, null).andExpect(jsonPath("$.owner.name").value("paula"));
		// ownerId: null explícito deixa sem Owner, mesmo com regra.
		create(otherPortfolio, ",\"ownerId\":null").andExpect(jsonPath("$.owner").doesNotExist());
	}

	@Test
	void papelComUmaPessoaPreencheComVariasPedeEscolhaEDepoisVoltaAHerdar() throws Exception {
		rule(portfolio, "INITIATIVE", "{\"mode\":\"ROLE\",\"role\":\"BUSINESS_OWNER\"}", "do.admin").andExpect(status().isOk());
		create(portfolio, null).andExpect(jsonPath("$.owner.name").value("bruno"));
		suggestion(portfolio).andExpect(jsonPath("$.mode").value("ROLE"))
			.andExpect(jsonPath("$.candidates.length()").value(1))
			.andExpect(jsonPath("$.suggestedId").isNotEmpty());

		grant("beatriz", "BUSINESS_OWNER", portfolio);
		create(portfolio, null).andExpect(jsonPath("$.owner").doesNotExist());
		String beatriz = JsonPath.read(suggestion(portfolio).andExpect(jsonPath("$.candidates.length()").value(2))
			.andExpect(jsonPath("$.suggestedId").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.candidates[1].id");
		create(portfolio, ",\"ownerId\":\"" + beatriz + "\"").andExpect(jsonPath("$.owner.name").value("beatriz"));

		// Ninguém com o papel no Portfólio: vale quem o tem na unidade acima mais próxima.
		rule(portfolio, "INITIATIVE", "{\"mode\":\"ROLE\",\"role\":\"QA_ENGINEER\"}", "do.admin").andExpect(status().isOk());
		create(portfolio, null).andExpect(jsonPath("$.owner.name").value("quintino"));
		suggestion(portfolio).andExpect(jsonPath("$.source.id").value(portfolio));

		mvc.perform(delete("/api/v1/org-units/{u}/default-owner-rules/INITIATIVE", portfolio).with(user("do.admin"))
			.with(csrf())).andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/org-units/{u}/default-owner-rules/INITIATIVE", portfolio).with(user("do.admin"))
			.with(csrf())).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("select count(*) from events.outbox_event where type like 'default_owner_rule.%'",
			Integer.class)).isGreaterThanOrEqualTo(3);
	}

	@Test
	void soQuemGereConfiguracaoGravaERegraInvalidaDa400() throws Exception {
		rule(portfolio, "FEATURE", "{\"mode\":\"CREATOR\"}", "paula").andExpect(status().isForbidden());
		rule(portfolio, "FEATURE", "{\"mode\":\"QUALQUER\"}", "do.admin").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("mode"));
		rule(portfolio, "FEATURE", "{\"mode\":\"ROLE\"}", "do.admin").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("role"));
		rule(portfolio, "FEATURE", "{\"mode\":\"ROLE\",\"role\":\"PLATFORM_ADMIN\"}", "do.admin")
			.andExpect(status().isBadRequest());
		rule(portfolio, "NAO_EXISTE", "{\"mode\":\"CREATOR\"}", "do.admin").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("artifactType"));
	}

	private ResultActions create(String unitId, String extra) throws Exception {
		return mvc.perform(json(post("/api/v1/artifacts"), user("paula"),
				"{\"type\":\"INITIATIVE\",\"title\":\"Iniciativa DO\",\"orgUnitId\":\"" + unitId + "\""
						+ (extra == null ? "" : extra) + "}"))
			.andExpect(status().isCreated());
	}

	private ResultActions rule(String unitId, String type, String body, String username) throws Exception {
		return mvc.perform(json(put("/api/v1/org-units/{u}/default-owner-rules/{t}", unitId, type), user(username), body));
	}

	private ResultActions suggestion(String unitId) throws Exception {
		return mvc.perform(get("/api/v1/org-units/{u}/default-owner", unitId).param("artifactType", "INITIATIVE")
			.with(user("paula"))).andExpect(status().isOk());
	}

	private String unit(String name, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("do.admin"),
				"{\"name\":\"" + name + "\",\"level\":\"PORTFOLIO\",\"parentId\":\"" + parentId + "\"}"))
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
