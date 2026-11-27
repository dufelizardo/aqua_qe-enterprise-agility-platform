package br.com.aquaqe.platform.configuration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/**
 * Campos customizados por tipo de artefato (#17; REV-12): catálogo do administrador, gravação só dos declarados e com o
 * tipo certo, gate que só cita campo declarado e remoção recusada enquanto um gate cita o campo.
 */
@SpringBootTest(properties = "aquaqe.access.admin-username=cf.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomFieldIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;
	String cadeia;

	@BeforeAll
	void organizacao() throws Exception {
		mvc.perform(get("/api/v1/me").with(user("cf.admin"))).andExpect(status().isOk());
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio CF", "PORTFOLIO", root);
		cadeia = unit("Cadeia CF", "CADEIA", portfolio);
		grant("cf.pm", "PRODUCT_MANAGER", portfolio);
	}

	@Test
	void catalogoSoDoAdministradorComChaveETipoValidos() throws Exception {
		field("cf.pm", "STORY", "{\"key\":\"origem\",\"label\":\"Origem\",\"type\":\"TEXT\"}")
			.andExpect(status().isForbidden());
		field("cf.admin", "STORY", "{\"key\":\"Origem\",\"label\":\"Origem\",\"type\":\"TEXT\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("key"));
		field("cf.admin", "STORY", "{\"key\":\"origem\",\"label\":\"Origem\",\"type\":\"LISTA\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("type"));
		field("cf.admin", "STORY", "{\"key\":\"canal\",\"label\":\"Canal\",\"type\":\"SELECT\",\"options\":[]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("options"));
		field("cf.admin", "NAO_EXISTE", "{\"key\":\"x\",\"label\":\"X\",\"type\":\"TEXT\"}")
			.andExpect(status().isNotFound());

		String id = JsonPath.read(field("cf.admin", "STORY",
				"{\"key\":\"origem\",\"label\":\"Origem\",\"type\":\"SELECT\",\"options\":[\"Cliente\",\"Interno\"]}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.position").value(1))
			.andReturn().getResponse().getContentAsString(), "$.id");
		field("cf.admin", "STORY", "{\"key\":\"origem\",\"label\":\"Outro\",\"type\":\"TEXT\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("key"));

		mvc.perform(get("/api/v1/custom-fields").param("artifactType", "STORY").with(user("cf.pm")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].key").value("origem"))
			.andExpect(jsonPath("$.items[0].options[1]").value("Interno"));
		mvc.perform(json(patch("/api/v1/custom-fields/{id}", id), user("cf.admin"), "{\"type\":\"TEXT\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("type"));
		mvc.perform(json(patch("/api/v1/custom-fields/{id}", id), user("cf.admin"),
				"{\"label\":\"Origem da demanda\",\"options\":[\"Cliente\",\"Interno\",\"Regulatório\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.label").value("Origem da demanda"))
			.andExpect(jsonPath("$.options.length()").value(3));
		mvc.perform(delete("/api/v1/custom-fields/{id}", id).with(user("cf.pm")).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/custom-fields/{id}", id).with(user("cf.admin")).with(csrf()))
			.andExpect(status().isNoContent());
		Integer events = jdbc.queryForObject(
			"select count(*) from events.outbox_event where type like 'custom_field.%'", Integer.class);
		org.assertj.core.api.Assertions.assertThat(events).isGreaterThanOrEqualTo(3);
	}

	@Test
	void gravacaoSoDosCamposDeclaradosComOTipoCerto() throws Exception {
		field("cf.admin", "INITIATIVE", "{\"key\":\"receita\",\"label\":\"Receita\",\"type\":\"NUMBER\"}")
			.andExpect(status().isCreated());
		field("cf.admin", "INITIATIVE", "{\"key\":\"lancamento\",\"label\":\"Lançamento\",\"type\":\"DATE\"}")
			.andExpect(status().isCreated());
		field("cf.admin", "INITIATIVE", "{\"key\":\"regulatorio\",\"label\":\"Regulatório\",\"type\":\"BOOLEAN\"}")
			.andExpect(status().isCreated());

		mvc.perform(json(post("/api/v1/artifacts"), user("cf.pm"), "{\"type\":\"INITIATIVE\",\"title\":\"X\",\"orgUnitId\":\""
				+ portfolio + "\",\"customFields\":{\"naoDeclarado\":\"x\"}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("customFields.naoDeclarado"));
		String key = JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("cf.pm"),
				"{\"type\":\"INITIATIVE\",\"title\":\"Pix\",\"orgUnitId\":\"" + portfolio
						+ "\",\"customFields\":{\"receita\":1500.5,\"lancamento\":\"2027-03-01\"}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.customFields.receita").value(1500.5))
			.andReturn().getResponse().getContentAsString(), "$.key");

		patchFields(key, 0, "{\"receita\":\"muito\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("customFields.receita"));
		patchFields(key, 0, "{\"lancamento\":\"01/03/2027\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].message").value(containsString("AAAA-MM-DD")));
		patchFields(key, 0, "{\"regulatorio\":\"sim\"}").andExpect(status().isBadRequest());
		// Merge Patch: só a chave enviada muda; null limpa.
		patchFields(key, 0, "{\"regulatorio\":true,\"receita\":null}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.customFields.regulatorio").value(true))
			.andExpect(jsonPath("$.customFields.lancamento").value("2027-03-01"))
			.andExpect(jsonPath("$.customFields.receita").doesNotExist());
	}

	@Test
	void gateSoCitaCampoDeclaradoENaoSeAplicaAoTipoQueNaoDeclara() throws Exception {
		String init = artifact("INITIATIVE", "Base", null, portfolio);
		String feature = artifact("FEATURE", "Checkout", init, cadeia);
		String sustaining = artifact("SUSTAINING_FEATURE", "Ajuste", null, cadeia);
		String transition = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}/transitions", feature).with(user("cf.pm")))
			.andReturn().getResponse().getContentAsString(), "$.items[0].id");
		String rule = "{\"name\":\"Com canal\",\"rules\":[{\"type\":\"REQUIRED_FIELD\",\"params\":{\"field\":\"customFields.canal\"},"
				+ "\"message\":\"Informe o canal.\"}]}";

		gate(transition, rule).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("rules[0].params.field"))
			.andExpect(jsonPath("$.errors[0].message").value(containsString("não está declarado")));
		String id = JsonPath.read(field("cf.admin", "FEATURE",
				"{\"key\":\"canal\",\"label\":\"Canal\",\"type\":\"SELECT\",\"options\":[\"App\",\"Web\"]}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		gate(transition, rule).andExpect(status().isOk());

		move(feature, transition).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("GATE_BLOCKED"));
		// A Feature de Sustentação usa o mesmo workflow, mas não declara o campo: a regra não se aplica.
		move(sustaining, transition).andExpect(status().isOk());
		patchFields(feature, version(feature), "{\"canal\":\"Web\"}").andExpect(status().isOk());
		move(feature, transition).andExpect(status().isOk());

		mvc.perform(delete("/api/v1/custom-fields/{id}", id).with(user("cf.admin")).with(csrf()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("FIELD_IN_USE"))
			.andExpect(jsonPath("$.detail").value(containsString("Com canal")));
		mvc.perform(delete("/api/v1/workflow-transitions/{id}/gate", transition).with(user("cf.admin")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/custom-fields/{id}", id).with(user("cf.admin")).with(csrf()))
			.andExpect(status().isNoContent());
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private ResultActions field(String username, String type, String body) throws Exception {
		return mvc.perform(json(post("/api/v1/artifact-types/{key}/custom-fields", type), user(username), body));
	}

	private ResultActions patchFields(String artifact, int version, String fields) throws Exception {
		return mvc.perform(json(patch("/api/v1/artifacts/{a}", artifact), user("cf.pm"),
				"{\"customFields\":" + fields + ",\"version\":" + version + "}"));
	}

	private ResultActions gate(String transitionId, String body) throws Exception {
		return mvc.perform(json(put("/api/v1/workflow-transitions/{id}/gate", transitionId), user("cf.admin"), body));
	}

	private int version(String artifact) throws Exception {
		return JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}", artifact).with(user("cf.pm")))
			.andReturn().getResponse().getContentAsString(), "$.version");
	}

	private ResultActions move(String artifact, String transition) throws Exception {
		return mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", artifact), user("cf.pm"),
				"{\"transitionId\":\"" + transition + "\",\"version\":" + version(artifact) + "}"));
	}

	private String artifact(String type, String title, String parent, String unitId) throws Exception {
		String parentId = parent == null ? null : JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}", parent)
			.with(user("cf.pm"))).andReturn().getResponse().getContentAsString(), "$.id");
		String extra = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("cf.pm"),
				"{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + unitId + "\"" + extra + "}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("cf.admin"),
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
