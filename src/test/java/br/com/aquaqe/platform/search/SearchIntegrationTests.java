package br.com.aquaqe.platform.search;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

/** {@code GET /search} (SSD 3.10; #84): termos sem acento e maiúsculas, trecho e regras de leitura. */
@SpringBootTest(properties = "aquaqe.access.admin-username=se.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchIntegrationTests {

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
		portfolio = unit("Portfólio SE", "PORTFOLIO", root);
		String outro = unit("Outro Portfólio SE", "PORTFOLIO", root);
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("otavio", "PRODUCT_MANAGER", outro);
	}

	@Test
	void artefatoPeloTituloEPelaDescricaoSemAcentoSoParaQuemLe() throws Exception {
		String key = artifact("Governança de Pagamentos", "Integração com o gateway PIX para lojistas.");
		search("paula", "GOVERNANCA").andExpect(status().isOk())
			.andExpect(jsonPath("$.artifacts[*].key").value(hasItem(key)));
		search("paula", "gateway integracao").andExpect(jsonPath("$.artifacts[0].key").value(key))
			.andExpect(jsonPath("$.artifacts[0].snippet").value(containsString("gateway PIX")))
			.andExpect(jsonPath("$.artifacts[0].orgUnit.name").value("Portfólio SE"));
		search("paula", "gateway boleto").andExpect(jsonPath("$.artifacts[*].key").value(not(hasItem(key))));
		search("otavio", "governanca").andExpect(jsonPath("$.artifacts[*].key").value(not(hasItem(key))));
	}

	@Test
	void paginaDoWikiPeloTextoDoEditor() throws Exception {
		String page = JsonPath.read(mvc.perform(json(post("/api/v1/org-units/{u}/pages", portfolio), user("paula"),
				"{\"title\":\"Guia de Onboarding\"}")).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id");
		String doc = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\","
				+ "\"text\":\"Antes do primeiro deploy, use o checklist de segurança.\"}]}]}";
		mvc.perform(json(put("/api/v1/pages/{id}", page), user("paula"),
				"{\"baseVersion\":1,\"sections\":[{\"key\":\"content\",\"blocks\":[{\"type\":\"richtext\",\"doc\":" + doc
						+ "}]}]}"))
			.andExpect(status().isOk());
		search("paula", "checklist seguranca").andExpect(jsonPath("$.pages[0].pageId").value(page))
			.andExpect(jsonPath("$.pages[0].title").value("Guia de Onboarding"))
			.andExpect(jsonPath("$.pages[0].type").value("PAGE"))
			.andExpect(jsonPath("$.pages[0].snippet").value(containsString("checklist de segurança")));
		search("otavio", "checklist seguranca").andExpect(jsonPath("$.pages[*].pageId").value(not(hasItem(page))));
	}

	@Test
	void documentoDoArtefatoPeloTextoDaVersaoAtual() throws Exception {
		String key = artifact("Checkout Rápido", null);
		List<String> keys = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}/documents/PRD", key).with(user("paula")))
			.andReturn().getResponse().getContentAsString(), "$.sections[*].key");
		String sections = keys.stream()
			.map(k -> "{\"key\":\"" + k + "\",\"blocks\":" + ("context".equals(k)
					? "[{\"type\":\"paragraph\",\"text\":\"Requisito de acessibilidade para leitores de tela.\"}]" : "[]")
					+ "}")
			.collect(Collectors.joining(",", "[", "]"));
		mvc.perform(json(put("/api/v1/artifacts/{a}/documents/PRD", key), user("paula"),
				"{\"baseVersion\":0,\"sections\":" + sections + "}"))
			.andExpect(status().isOk());
		search("paula", "acessibilidade").andExpect(jsonPath("$.documents[0].key").value(key))
			.andExpect(jsonPath("$.documents[0].documentType").value("PRD"))
			.andExpect(jsonPath("$.documents[0].title").value("Checkout Rápido"));
		search("otavio", "acessibilidade").andExpect(jsonPath("$.documents[*].key").value(not(hasItem(key))));
		mvc.perform(get("/api/v1/search").param("q", "acessibilidade").param("kind", "PAGE").with(user("paula")))
			.andExpect(jsonPath("$.documents").isEmpty());
	}

	@Test
	void buscaCurtaOuTipoInvalidoE400() throws Exception {
		search("paula", "a").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("q"));
		mvc.perform(get("/api/v1/search").param("q", "abc").param("kind", "OUTRO").with(user("paula")))
			.andExpect(status().isBadRequest());
		// Curingas digitados contam como texto.
		search("paula", "%%").andExpect(status().isOk()).andExpect(jsonPath("$.artifacts").isEmpty());
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private ResultActions search(String username, String q) throws Exception {
		return mvc.perform(get("/api/v1/search").param("q", q).with(user(username)));
	}

	private String artifact(String title, String description) throws Exception {
		String extra = description == null ? "" : ",\"description\":\"" + description + "\"";
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("paula"),
				"{\"type\":\"INITIATIVE\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + portfolio + "\"" + extra + "}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("se.admin"),
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
