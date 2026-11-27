package br.com.aquaqe.platform.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
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

import tools.jackson.databind.ObjectMapper;

/**
 * Workflow Designer (#49; SSD 3.3; F05/D-01): workflow próprio por unidade, com herança para as de baixo, versões
 * imutáveis, gates copiados e migração explícita dos itens.
 */
@SpringBootTest(properties = "aquaqe.access.admin-username=wd.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowDesignerIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	final ObjectMapper json = new ObjectMapper();

	String root;
	String rootFeatureWorkflow;

	@BeforeAll
	void organizacao() throws Exception {
		mvc.perform(get("/api/v1/me").with(user("wd.admin"))).andExpect(status().isOk());
		root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		rootFeatureWorkflow = JsonPath.read(mvc.perform(get("/api/v1/workflows").param("artifactType", "SUSTAINING_FEATURE")
			.with(user("wd.admin"))).andReturn().getResponse().getContentAsString(), "$.items[0].id");
	}

	@Test
	void personalizarPublicarEMigrarSemMudancaSilenciosa() throws Exception {
		String portfolio = unit("Portfólio WD", "PORTFOLIO", root);
		String cadeia = unit("Cadeia WD", "CADEIA", portfolio);
		String abaixo = unit("Squad WD", "SQUAD", cadeia);
		grant("wd.pm", "PRODUCT_MANAGER", cadeia);

		// Gate na transição da raiz: vai junto na cópia.
		String rootBacklogToRefinement = transitionId(rootFeatureWorkflow, "BACKLOG", "REFINEMENT");
		mvc.perform(json(put("/api/v1/workflow-transitions/{id}/gate", rootBacklogToRefinement), user("wd.admin"),
				"{\"name\":\"Pronto para refinar\",\"rules\":[{\"type\":\"REQUIRED_FIELD\",\"params\":{\"field\":\"size\"},"
						+ "\"message\":\"Informe a estimativa.\"}]}"))
			.andExpect(status().isOk());

		mvc.perform(get("/api/v1/org-units/{u}/workflows", cadeia).with(user("wd.pm")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.workflow.id == '" + rootFeatureWorkflow + "')].inherited").value(contains(true)));
		String antes = artifact(cadeia, "Item de antes");

		// Personalizar: cópia v1 na Cadeia; vale para ela e para as de baixo.
		String v1 = JsonPath.read(mvc.perform(json(post("/api/v1/org-units/{u}/workflows", cadeia), user("wd.admin"),
				"{\"from\":\"" + rootFeatureWorkflow + "\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.orgUnit.id").value(cadeia))
			.andExpect(jsonPath("$.artifactTypes", hasItem("SUSTAINING_FEATURE")))
			.andReturn().getResponse().getContentAsString(), "$.id");
		assertThat(jdbc.queryForObject("""
				select count(*) from governance.gate g join configuration.workflow_transition t on t.id = g.transition_id
				 where t.workflow_id = ?::uuid
				""", Integer.class, v1)).isEqualTo(1);
		mvc.perform(get("/api/v1/org-units/{u}/workflows", abaixo).with(user("wd.admin")))
			.andExpect(jsonPath("$.items[?(@.workflow.id == '" + v1 + "')].inherited").value(contains(true)))
			.andExpect(jsonPath("$.items[?(@.workflow.id == '" + v1 + "')].source.id").value(contains(cadeia)));
		mvc.perform(json(post("/api/v1/org-units/{u}/workflows", cadeia), user("wd.admin"),
				"{\"from\":\"" + v1 + "\"}"))
			.andExpect(status().isBadRequest());

		String depois = artifact(cadeia, "Item de depois");
		assertThat(workflowOf(antes)).isEqualTo(rootFeatureWorkflow);
		assertThat(workflowOf(depois)).isEqualTo(v1);

		// O item de antes fica no workflow herdado até a migração.
		mvc.perform(get("/api/v1/workflows/{id}/migration", v1).with(user("wd.admin")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.pending[0].statusKey").value("BACKLOG"))
			.andExpect(jsonPath("$.pending[0].suggestedKey").value("BACKLOG"));

		// v2: Backlog vira Ideia (chave nova) e entra "Em Triagem".
		String v2 = JsonPath.read(publish(v1, versionBody(v1)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.versionNumber").value(2))
			.andExpect(jsonPath("$.statuses[0].key").value("IDEA"))
			.andExpect(jsonPath("$.statuses[0].position").value(1))
			.andReturn().getResponse().getContentAsString(), "$.id");
		publish(v1, versionBody(v1)).andExpect(status().isConflict());
		assertThat(workflowOf(depois)).isEqualTo(v1);
		assertThat(jdbc.queryForObject("select count(*) from events.outbox_event where type = 'workflow.version_published'",
			Integer.class)).isGreaterThanOrEqualTo(1);
		// O gate vai pela chave dos status: BACKLOG → REFINEMENT não existe na v2 (virou IDEA → REFINEMENT), então ele
		// fica só na v1, onde ainda valem os itens que não migraram.
		assertThat(jdbc.queryForObject("""
				select count(*) from governance.gate g join configuration.workflow_transition t on t.id = g.transition_id
				 where t.workflow_id = ?::uuid
				""", Integer.class, v2)).isZero();
		assertThat(jdbc.queryForObject("""
				select count(*) from governance.gate g join configuration.workflow_transition t on t.id = g.transition_id
				 where t.workflow_id = ?::uuid
				""", Integer.class, v1)).isEqualTo(1);

		// O quadro novo da Cadeia já tem as colunas da v2.
		mvc.perform(json(post("/api/v1/boards"), user("wd.admin"), "{\"orgUnitId\":\"" + cadeia + "\",\"level\":\"FL2\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.columns[*].name", hasItem("Ideia")))
			.andExpect(jsonPath("$.columns[*].name", hasItem("Em Triagem")));

		mvc.perform(get("/api/v1/workflows/{id}/migration", v2).with(user("wd.admin")))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.pending[0].statusKey").value("BACKLOG"))
			.andExpect(jsonPath("$.pending[0].items").value(2))
			.andExpect(jsonPath("$.pending[0].suggestedKey").doesNotExist());
		migrate(v2, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("mapping.BACKLOG"));
		migrate(v2, "{\"mapping\":{\"BACKLOG\":\"NOPE\"}}").andExpect(status().isBadRequest());
		migrate(v2, "{\"mapping\":{\"BACKLOG\":\"IDEA\"}}").andExpect(status().isOk())
			.andExpect(jsonPath("$.migrated").value(2));
		migrate(v2, "{\"mapping\":{\"BACKLOG\":\"IDEA\"}}").andExpect(jsonPath("$.migrated").value(0));
		assertThat(workflowOf(antes)).isEqualTo(v2);
		assertThat(workflowOf(depois)).isEqualTo(v2);
		mvc.perform(get("/api/v1/artifacts/{a}", antes).with(user("wd.admin")))
			.andExpect(jsonPath("$.status.key").value("IDEA"));
		assertThat(jdbc.queryForObject("select count(*) from events.outbox_event where type = 'artifact.workflow_migrated'",
			Integer.class)).isEqualTo(2);

		// Voltar a herdar: os itens ficam na v2 até migrar para o da raiz.
		mvc.perform(delete("/api/v1/workflows/{id}", v2).with(user("wd.admin")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/org-units/{u}/workflows", cadeia).with(user("wd.admin")))
			.andExpect(jsonPath("$.items[?(@.workflow.id == '" + rootFeatureWorkflow + "')].inherited").value(contains(true)));
		assertThat(workflowOf(antes)).isEqualTo(v2);
		mvc.perform(get("/api/v1/workflows/{id}/migration", rootFeatureWorkflow).with(user("wd.admin")))
			.andExpect(jsonPath("$.pending[?(@.statusKey == 'IDEA')].items").value(contains(2)));
	}

	@Test
	void soQuemGereConfiguracaoEVersoesValidas() throws Exception {
		String portfolio = unit("Portfólio WD 2", "PORTFOLIO", root);
		String cadeia = unit("Cadeia WD 2", "CADEIA", portfolio);
		grant("wd.pm", "PRODUCT_MANAGER", cadeia);
		mvc.perform(json(post("/api/v1/org-units/{u}/workflows", cadeia), user("wd.pm"),
				"{\"from\":\"" + rootFeatureWorkflow + "\"}"))
			.andExpect(status().isForbidden());
		String own = JsonPath.read(mvc.perform(json(post("/api/v1/org-units/{u}/workflows", cadeia), user("wd.admin"),
				"{\"from\":\"" + rootFeatureWorkflow + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(json(post("/api/v1/workflows/{id}/versions", own), user("wd.pm"), versionBody(own)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/workflows/{id}/migration", own).with(user("wd.pm"))).andExpect(status().isForbidden());

		String ok = "{\"key\":\"A\",\"name\":\"A\",\"category\":\"TODO\"}";
		publish(own, "{\"statuses\":[]}").andExpect(jsonPath("$.errors[0].field").value("statuses"));
		publish(own, "{\"statuses\":[" + ok + "," + ok + "]}").andExpect(jsonPath("$.errors[0].field").value("statuses[1].key"));
		publish(own, "{\"statuses\":[{\"key\":\"a b\",\"name\":\"A\",\"category\":\"TODO\"}]}")
			.andExpect(jsonPath("$.errors[0].field").value("statuses[0].key"));
		publish(own, "{\"statuses\":[{\"key\":\"A\",\"name\":\"A\",\"category\":\"FEITO\"}]}")
			.andExpect(jsonPath("$.errors[0].field").value("statuses[0].category"));
		publish(own, "{\"statuses\":[{\"key\":\"A\",\"name\":\"A\",\"category\":\"TODO\",\"inSequence\":false}]}")
			.andExpect(jsonPath("$.errors[0].field").value("statuses"));
		publish(own, "{\"statuses\":[" + ok + "],\"transitions\":[{\"from\":\"A\",\"to\":\"B\"}]}")
			.andExpect(jsonPath("$.errors[0].field").value("transitions[0]"));
		publish(own, "{\"statuses\":[" + ok + "],\"transitions\":[{\"from\":\"A\",\"to\":\"A\"}]}")
			.andExpect(jsonPath("$.errors[0].field").value("transitions[0]"));

		mvc.perform(delete("/api/v1/workflows/{id}", rootFeatureWorkflow).with(user("wd.admin")).with(csrf()))
			.andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------------------------------------------- apoio

	/** Corpo da v2 a partir da versão informada: BACKLOG vira IDEA e entra TRIAGE depois de IDEA. */
	@SuppressWarnings("unchecked")
	private String versionBody(String workflowId) throws Exception {
		Map<String, Object> workflow = json.readValue(mvc.perform(get("/api/v1/workflows/{id}", workflowId)
			.with(user("wd.admin"))).andReturn().getResponse().getContentAsString(), Map.class);
		List<Map<String, Object>> statuses = new java.util.ArrayList<>();
		for (Map<String, Object> s : (List<Map<String, Object>>) workflow.get("statuses")) {
			String key = "BACKLOG".equals(s.get("key")) ? "IDEA" : (String) s.get("key");
			String name = "BACKLOG".equals(s.get("key")) ? "Ideia" : (String) s.get("name");
			Map<String, Object> status = new java.util.LinkedHashMap<>();
			status.put("key", key);
			status.put("name", name);
			status.put("phase", s.get("phase"));
			status.put("category", s.get("category"));
			status.put("inSequence", s.get("position") != null);
			statuses.add(status);
			if ("IDEA".equals(key)) {
				statuses.add(Map.of("key", "TRIAGE", "name", "Em Triagem", "phase", "UPSTREAM", "category",
					"IN_PROGRESS", "inSequence", true));
			}
		}
		List<Map<String, Object>> transitions = new java.util.ArrayList<>();
		for (Map<String, Object> t : (List<Map<String, Object>>) workflow.get("transitions")) {
			transitions.add(Map.of("from", rename((String) t.get("from")), "to", rename((String) t.get("to"))));
		}
		transitions.add(Map.of("from", "IDEA", "to", "TRIAGE"));
		return json.writeValueAsString(Map.of("statuses", statuses, "transitions", transitions));
	}

	private static String rename(String key) {
		return "BACKLOG".equals(key) ? "IDEA" : key;
	}

	private ResultActions publish(String workflowId, String body) throws Exception {
		return mvc.perform(json(post("/api/v1/workflows/{id}/versions", workflowId), user("wd.admin"), body));
	}

	private ResultActions migrate(String workflowId, String body) throws Exception {
		return mvc.perform(json(post("/api/v1/workflows/{id}/migration", workflowId), user("wd.admin"), body));
	}

	private String transitionId(String workflowId, String from, String to) {
		return jdbc.queryForObject("""
				select t.id from configuration.workflow_transition t
				  join configuration.workflow_status f on f.id = t.from_status_id
				  join configuration.workflow_status s on s.id = t.to_status_id
				 where t.workflow_id = ?::uuid and f.key = ? and s.key = ?
				""", UUID.class, workflowId, from, to).toString();
	}

	private String workflowOf(String artifactId) {
		return jdbc.queryForObject("""
				select s.workflow_id from workitem.artifact a join configuration.workflow_status s on s.id = a.status_id
				 where a.id = ?::uuid
				""", UUID.class, artifactId).toString();
	}

	private String artifact(String unitId, String title) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("wd.admin"),
				"{\"type\":\"SUSTAINING_FEATURE\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + unitId + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("wd.admin"),
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
