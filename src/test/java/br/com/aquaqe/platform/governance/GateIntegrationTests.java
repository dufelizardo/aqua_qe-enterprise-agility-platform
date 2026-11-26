package br.com.aquaqe.platform.governance;

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

/** Gates por transição (SSD 5; #78): configuração, bloqueio, painel, aprovação por papel e reabertura de documento. */
@SpringBootTest(properties = "aquaqe.access.admin-username=ga.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GateIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;
	String cadeia;
	/** Primeira transição do workflow da Iniciativa (a do status inicial). */
	String transition;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio GA", "PORTFOLIO", root);
		cadeia = unit("Cadeia GA", "CADEIA", portfolio);
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("tiago", "TECH_LEAD", portfolio);
		String init = artifact("INITIATIVE", "Descoberta", null);
		transition = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}/transitions", init).with(user("paula")))
			.andReturn().getResponse().getContentAsString(), "$.items[0].id");
	}

	@Test
	void configurarOGateSoParaQuemGereConfiguracaoEComRegrasValidas() throws Exception {
		String other = JsonPath.read(mvc.perform(get("/api/v1/workflows").param("artifactType", "STORY").with(user("ga.admin")))
			.andReturn().getResponse().getContentAsString(), "$.items[0].transitions[0].id");
		gate("paula", transition, "{\"name\":\"X\",\"rules\":[{\"type\":\"REQUIRED_FIELD\",\"params\":{\"field\":\"size\"},"
				+ "\"message\":\"m\"}]}").andExpect(status().isForbidden());
		gate("ga.admin", transition, "{\"name\":\"X\",\"rules\":[{\"type\":\"BUSINESS_CASE_VALIDATED\",\"message\":\"m\"}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("rules[0].type"))
			.andExpect(jsonPath("$.errors[0].message").value(containsString("business case")));
		gate("ga.admin", transition, "{\"name\":\"X\",\"rules\":[{\"type\":\"DOCUMENT_SECTION_FILLED\","
				+ "\"params\":{\"documentType\":\"PRD\",\"sectionKey\":\"nao-existe\"},\"message\":\"m\"}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("rules[0].params.sectionKey"));
		gate("ga.admin", other, "{\"name\":\"X\",\"rules\":[{\"type\":\"DOCUMENT_APPROVED\","
				+ "\"params\":{\"documentType\":\"PRD\"},\"message\":\"m\"}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("rules[0].params.documentType"));
		gate("ga.admin", other, "{\"name\":\"X\",\"rules\":[{\"type\":\"REQUIRED_FIELD\","
				+ "\"params\":{\"field\":\"acceptanceCriteria\"},\"message\":\"m\"}]}")
			.andExpect(status().isBadRequest());
		gate("ga.admin", other, "{\"name\":\"Pronto\",\"rules\":[{\"type\":\"REQUIRED_FIELD\","
				+ "\"params\":{\"field\":\"assigneeId\"},\"message\":\"Atribua alguém.\"}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rules[0].category").value("VALIDATION"));
		String workflowId = JsonPath.read(mvc.perform(get("/api/v1/workflows").param("artifactType", "STORY")
			.with(user("ga.admin"))).andReturn().getResponse().getContentAsString(), "$.items[0].id");
		mvc.perform(get("/api/v1/gates").param("workflowId", workflowId).with(user("paula")))
			.andExpect(jsonPath("$.items.length()").value(1));
		mvc.perform(get("/api/v1/gates").with(user("ga.admin")))
			.andExpect(jsonPath("$.items[?(@.transitionId == '" + other + "')].rules").value(org.hamcrest.Matchers.contains(1)));
		mvc.perform(delete("/api/v1/workflow-transitions/{id}/gate", other).with(user("ga.admin")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/workflow-transitions/{id}/gate", other).with(user("ga.admin")))
			.andExpect(status().isNotFound());
	}

	/** #95: o primeiro acesso de um usuário pode ser uma rota somente leitura; a conta é criada mesmo assim. */
	@Test
	void oPrimeiroAcessoNumaRotaSomenteLeituraCriaAConta() throws Exception {
		String init = artifact("INITIATIVE", "Primeiro acesso", null);
		mvc.perform(get("/api/v1/artifacts/{a}/gates", init).with(user("novato.gates")))
			.andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isLessThan(500));
		mvc.perform(get("/api/v1/me/notifications").with(user("novato.notificacoes"))).andExpect(status().isOk());
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
			"select count(*) from identity.user_account where subject in ('sub-novato.gates', 'sub-novato.notificacoes')",
			Integer.class)).isEqualTo(2);
	}

	@Test
	void oGateBloqueiaAteTodasAsRegrasEReabrirODocumentoVoltaABloquear() throws Exception {
		gate("ga.admin", transition, """
				{"name":"Pronto para Construir","rules":[
				 {"type":"REQUIRED_FIELD","params":{"field":"size"},"message":"Informe a estimativa."},
				 {"type":"DOCUMENT_SECTION_FILLED","params":{"documentType":"PRD","sectionKey":"context"},"message":"Contexto do PRD."},
				 {"type":"DOCUMENT_APPROVED","params":{"documentType":"PRD"},"message":"Aprove o PRD."},
				 {"type":"MIN_CHILDREN","params":{"childType":"FEATURE","min":1},"message":"Crie uma Feature."},
				 {"type":"ROLE_APPROVAL","params":{"role":"TECH_LEAD","label":"Architecture Gate"},"message":"Aguardando arquitetura."}]}
				""").andExpect(status().isOk());
		String init = artifact("INITIATIVE", "Checkout com gate", null);

		move(init).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("GATE_BLOCKED"))
			.andExpect(jsonPath("$.failedRules.length()").value(5))
			.andExpect(jsonPath("$.failedRules[4].category").value("APPROVAL"))
			.andExpect(jsonPath("$.failedRules[0].params.field").value("size"))
			.andExpect(jsonPath("$.failedRules[0].gateName").value("Pronto para Construir"));
		mvc.perform(get("/api/v1/artifacts/{a}/gates", init).with(user("paula")))
			.andExpect(jsonPath("$.items[0].name").value("Pronto para Construir"))
			.andExpect(jsonPath("$.items[0].satisfied").value(0))
			.andExpect(jsonPath("$.items[0].total").value(5))
			.andExpect(jsonPath("$.items[0].rules[4].canDecide").value(false));
		mvc.perform(get("/api/v1/artifacts/{a}/gates", init).with(user("tiago")))
			.andExpect(jsonPath("$.items[0].rules[4].canDecide").value(true));

		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"size\":\"M\",\"version\":0}"))
			.andExpect(status().isOk());
		savePrd(init);
		prdStatus(init, "APPROVED").andExpect(status().isOk());
		artifact("FEATURE", "Pagamento", init);
		String rule = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}/gates", init).with(user("tiago")))
			.andReturn().getResponse().getContentAsString(), "$.items[0].rules[4].ruleId");
		approve("paula", init, rule, "APPROVED", null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("APPROVER_ROLE_REQUIRED"));
		approve("tiago", init, UUID.randomUUID().toString(), "APPROVED", null).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("APPROVAL_NOT_APPLICABLE"));
		approve("tiago", init, rule, "REJECTED", null).andExpect(status().isBadRequest());
		approve("tiago", init, rule, "REJECTED", "Falta o diagrama.").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].rules[4].satisfied").value(false))
			.andExpect(jsonPath("$.items[0].rules[4].lastDecision.decision").value("REJECTED"));
		approve("tiago", init, rule, "APPROVED", "Ok").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].satisfied").value(5))
			.andExpect(jsonPath("$.items[0].rules[4].lastDecision.by.name").value("tiago"));
		mvc.perform(get("/api/v1/artifacts/{a}/approvals", init).with(user("paula")))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].decision").value("APPROVED"))
			.andExpect(jsonPath("$.items[1].comment").value("Falta o diagrama."));

		// Reabrir o PRD volta a bloquear a regra que o exige aprovado (SSD 3.7).
		prdStatus(init, "IN_PROGRESS").andExpect(status().isOk());
		mvc.perform(get("/api/v1/artifacts/{a}/gates", init).with(user("paula")))
			.andExpect(jsonPath("$.items[0].satisfied").value(4))
			.andExpect(jsonPath("$.items[0].rules[2].satisfied").value(false));
		move(init).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.failedRules.length()").value(1))
			.andExpect(jsonPath("$.failedRules[0].type").value("DOCUMENT_APPROVED"));
		prdStatus(init, "APPROVED").andExpect(status().isOk());
		move(init).andExpect(status().isOk());
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private ResultActions gate(String username, String transitionId, String body) throws Exception {
		return mvc.perform(json(put("/api/v1/workflow-transitions/{id}/gate", transitionId), user(username), body));
	}

	private ResultActions move(String artifact) throws Exception {
		int version = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}", artifact).with(user("paula")))
			.andReturn().getResponse().getContentAsString(), "$.version");
		return mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", artifact), user("paula"),
				"{\"transitionId\":\"" + transition + "\",\"version\":" + version + "}"));
	}

	private ResultActions approve(String username, String artifact, String rule, String decision, String comment)
			throws Exception {
		String extra = comment == null ? "" : ",\"comment\":\"" + comment + "\"";
		return mvc.perform(json(post("/api/v1/artifacts/{a}/approvals", artifact), user(username),
				"{\"gateRuleId\":\"" + rule + "\",\"decision\":\"" + decision + "\"" + extra + "}"));
	}

	private void savePrd(String artifact) throws Exception {
		List<String> keys = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}/documents/PRD", artifact)
			.with(user("paula"))).andReturn().getResponse().getContentAsString(), "$.sections[*].key");
		String sections = keys.stream()
			.map(k -> "{\"key\":\"" + k + "\",\"blocks\":[{\"type\":\"paragraph\",\"text\":\"Texto de " + k + "\"}]}")
			.collect(Collectors.joining(",", "[", "]"));
		mvc.perform(json(put("/api/v1/artifacts/{a}/documents/PRD", artifact), user("paula"),
				"{\"baseVersion\":0,\"sections\":" + sections + "}"))
			.andExpect(status().isOk());
	}

	private ResultActions prdStatus(String artifact, String to) throws Exception {
		return mvc.perform(json(post("/api/v1/artifacts/{a}/documents/PRD/status", artifact), user("paula"),
				"{\"status\":\"" + to + "\",\"comment\":\"ok\"}"));
	}

	private String artifact(String type, String title, String parent) throws Exception {
		String parentId = parent == null ? null : JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}", parent)
			.with(user("paula"))).andReturn().getResponse().getContentAsString(), "$.id");
		String extra = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
		String id = JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("paula"),
				"{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + (parent == null ? portfolio : cadeia)
						+ "\"" + extra + "}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
		return id;
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("ga.admin"),
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
