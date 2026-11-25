package br.com.aquaqe.platform.livingdocs;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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

import org.hamcrest.MatcherAssert;
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

/** Documentos da Iniciativa e da Feature (SSD 3.7; PRD-RF-018 a 020; #63). */
@SpringBootTest(properties = "aquaqe.access.admin-username=ad.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtifactDocumentsIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;
	String cadeia;
	String outroPortfolio;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio AD", "PORTFOLIO", root);
		cadeia = unit("Cadeia AD", "CADEIA", portfolio);
		outroPortfolio = unit("Outro Portfólio AD", "PORTFOLIO", root);
		grant("pedro", "PRODUCT_MANAGER", portfolio);
		grant("tito", "TECH_LEAD", portfolio);
		grant("vera", "VIEWER", portfolio);
		grant("otto", "PRODUCT_MANAGER", outroPortfolio);
	}

	@Test
	void iniciativaEFeatureNascemComOsCincoDocumentosPendentes() throws Exception {
		String init = artifact("{\"type\":\"INITIATIVE\",\"title\":\"Docs\",\"orgUnitId\":\"" + portfolio + "\"}");
		String feat = artifact("{\"type\":\"FEATURE\",\"title\":\"Docs F\",\"orgUnitId\":\"" + cadeia
				+ "\",\"parentId\":\"" + jdbc.queryForObject("select id::text from workitem.artifact where key = ?",
					String.class, init) + "\"}");
		for (String key : List.of(init, feat)) {
			mvc.perform(get("/api/v1/artifacts/{a}/documents", key).with(user("pedro")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].type").value(contains("PRD", "SAD", "SSD", "UX", "QA")))
				.andExpect(jsonPath("$.items[*].status").value(everyItem(is("PENDING"))))
				.andExpect(jsonPath("$.items[*].currentVersion").value(everyItem(is(0))));
		}
		mvc.perform(get("/api/v1/artifacts/{a}/documents/{t}", init, "PRD").with(user("pedro")))
			.andExpect(jsonPath("$.versionNumber").value(0))
			.andExpect(jsonPath("$.sections[*].key")
				.value(contains("context", "objectives", "scope", "requirements", "metrics", "risks")))
			.andExpect(jsonPath("$.canEdit").value(true))
			.andExpect(jsonPath("$.canApprove").value(true));

		String sust = artifact("{\"type\":\"SUSTAINING_FEATURE\",\"title\":\"Sem docs\",\"orgUnitId\":\"" + cadeia + "\"}");
		mvc.perform(get("/api/v1/artifacts/{a}/documents", sust).with(user("pedro"))).andExpect(status().isNotFound());
	}

	@Test
	void salvarAprovarEReabrirComVersoes() throws Exception {
		String init = artifact("{\"type\":\"INITIATIVE\",\"title\":\"Ciclo\",\"orgUnitId\":\"" + portfolio + "\"}");
		save("pedro", init, 0, sections(init, "context")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.missingSections").value(contains("objectives", "scope", "requirements")));
		save("pedro", init, 0, sections(init, "context")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
		changeStatus("pedro", init, "APPROVED", null).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("DOCUMENT_INCOMPLETE"));

		save("pedro", init, 1, sections(init, "context", "objectives", "scope", "requirements")).andExpect(status().isOk())
			.andExpect(jsonPath("$.versionNumber").value(2))
			.andExpect(jsonPath("$.missingSections").isEmpty());
		changeStatus("tito", init, "APPROVED", null).andExpect(status().isForbidden());
		changeStatus("vera", init, "APPROVED", null).andExpect(status().isForbidden());
		changeStatus("pedro", init, "APPROVED", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.approvedVersion").value(2));

		save("pedro", init, 2, sections(init, "context")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_EDITABLE"));
		changeStatus("pedro", init, "IN_PROGRESS", null).andExpect(status().isBadRequest());
		changeStatus("pedro", init, "IN_PROGRESS", "Faltou o escopo de dados").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.approvedVersion").doesNotExist());

		mvc.perform(get("/api/v1/artifacts/{a}/documents/PRD/versions", init).with(user("pedro")))
			.andExpect(jsonPath("$[*].number").value(contains(2, 1)));
		mvc.perform(get("/api/v1/artifacts/{a}/documents/PRD/versions/{n}", init, 1).with(user("vera")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.canEdit").value(false));

		String docId = jdbc.queryForObject("""
				select d.id::text from livingdocs.document d join workitem.artifact a on a.id = d.artifact_id
				 where a.key = ? and d.type = 'PRD'
				""", String.class, init);
		List<String> types = jdbc.queryForList(
			"select type from events.outbox_event where envelope -> 'aggregate' ->> 'id' = ?", String.class, docId);
		MatcherAssert.assertThat(types, containsInAnyOrder("document.saved", "document.saved", "document.approved",
			"document.reopened"));
	}

	@Test
	void leitorNaoEditaEQuemNaoLeNaoVe() throws Exception {
		String init = artifact("{\"type\":\"INITIATIVE\",\"title\":\"Acesso\",\"orgUnitId\":\"" + portfolio + "\"}");
		mvc.perform(get("/api/v1/artifacts/{a}/documents", init).with(user("vera")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].canEdit").value(everyItem(is(false))));
		save("vera", init, 0, sections(init, "context")).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/artifacts/{a}/documents", init).with(user("otto"))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/artifacts/{a}/documents/XYZ", init).with(user("pedro"))).andExpect(status().isNotFound());
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private String artifact(String body) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("pedro"), body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
	}

	private ResultActions save(String username, String artifact, int base, String sectionsBody) throws Exception {
		String body = "{\"baseVersion\":" + base + "," + sectionsBody.substring(1);
		return mvc.perform(json(put("/api/v1/artifacts/{a}/documents/PRD", artifact), user(username), body));
	}

	private ResultActions changeStatus(String username, String artifact, String to, String comment) throws Exception {
		String body = "{\"status\":\"" + to + "\"" + (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}";
		return mvc.perform(json(post("/api/v1/artifacts/{a}/documents/PRD/status", artifact), user(username), body));
	}

	/** Todas as seções do PRD; as informadas com texto, as demais vazias. */
	private String sections(String artifact, String... filled) throws Exception {
		List<String> keys = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}/documents/PRD", artifact)
			.with(user("pedro"))).andReturn().getResponse().getContentAsString(), "$.sections[*].key");
		List<String> withText = List.of(filled);
		return keys.stream()
			.map(k -> "{\"key\":\"" + k + "\",\"blocks\":"
					+ (withText.contains(k) ? "[{\"type\":\"paragraph\",\"text\":\"Texto de " + k + "\"}]" : "[]") + "}")
			.collect(Collectors.joining(",", "{\"sections\":[", "]}"));
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("ad.admin"),
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
