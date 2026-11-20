package br.com.aquaqe.platform.workflow;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
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
 * Transições de status (SSD 3.5 e 4.2). Uma guarda de teste bloqueia toda ida para Cancelado, no lugar do gate que o
 * módulo governance vai trazer.
 */
@SpringBootTest(properties = "aquaqe.access.admin-username=wf.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkflowIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@TestConfiguration
	static class GuardaDeTeste {

		@Bean
		TransitionGuard bloqueiaCancelamento() {
			return (artifact, transition) -> "CANCELLED".equals(transition.to().key())
					? List.of(new TransitionGuard.FailedRule("regra-teste", "ROLE_APPROVAL", "Cancelar exige aprovação."))
					: List.of();
		}
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;
	String cadeia;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		String outroPortfolio = unit("Portfólio Outro", "PORTFOLIO", root);
		portfolio = unit("Portfólio WF", "PORTFOLIO", root);
		cadeia = unit("Cadeia WF", "CADEIA", outroPortfolio);
		mvc.perform(put("/api/v1/org-units/{id}/links/{target}", cadeia, portfolio).with(user("wf.admin")).with(csrf()))
			.andExpect(status().isNoContent());
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("tiago", "TECH_LEAD", cadeia);
		grant("carlos", "PRODUCT_MANAGER", unit("Portfólio Terceiro", "PORTFOLIO", root));
	}

	@Test
	void transicoesPossiveisAPartirDoStatusAtual() throws Exception {
		String init = initiative();
		mvc.perform(get("/api/v1/artifacts/{a}/transitions", init).with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].to.key").value(containsInAnyOrder("BACKLOG", "CANCELLED")))
			.andExpect(jsonPath("$.items[?(@.to.key=='BACKLOG')].canExecute").value(containsInAnyOrder(true)))
			.andExpect(jsonPath("$.items[?(@.to.key=='CANCELLED')].failedRules[0].message")
				.value(containsInAnyOrder("Cancelar exige aprovação.")));
	}

	@Test
	void executaTransicaoEGravaHistorico() throws Exception {
		String init = initiative();
		transition(init, "paula", "BACKLOG", 0, "Pronta para o backlog.")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status.key").value("BACKLOG"))
			.andExpect(jsonPath("$.version").value(1));
		mvc.perform(get("/api/v1/artifacts/{a}/history", init).with(user("paula")))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].from").value("IDEA"))
			.andExpect(jsonPath("$.items[0].to").value("BACKLOG"))
			.andExpect(jsonPath("$.items[0].by.name").value("paula"))
			.andExpect(jsonPath("$.items[0].comment").value("Pronta para o backlog."));
	}

	@Test
	void versaoDesatualizadaRecebe409() throws Exception {
		String init = initiative();
		transition(init, "paula", "BACKLOG", 5, null)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
	}

	@Test
	void transicaoQueNaoSaiDoStatusAtualRecebe422() throws Exception {
		String init = initiative();
		// De IDEA não se vai direto para FEASIBILITY: a transição existe no workflow, mas sai de BACKLOG.
		String fromBacklog = jdbc.queryForObject("""
				select tr.id from configuration.workflow_transition tr
				  join configuration.workflow_status f on f.id = tr.from_status_id
				  join configuration.workflow_status t on t.id = tr.to_status_id
				  join configuration.workflow_artifact_type wt on wt.workflow_id = tr.workflow_id
				  join configuration.artifact_type at on at.id = wt.artifact_type_id
				 where at.key = 'INITIATIVE' and f.key = 'BACKLOG' and t.key = 'FEASIBILITY'
				""", UUID.class).toString();
		mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", init), user("paula"),
				"{\"transitionId\":\"" + fromBacklog + "\",\"version\":0}"))
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
	}

	@Test
	void gateBloqueadoNaoGravaNada() throws Exception {
		String init = initiative();
		transition(init, "paula", "CANCELLED", 0, null)
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("GATE_BLOCKED"))
			.andExpect(jsonPath("$.failedRules[0].type").value("ROLE_APPROVAL"));
		mvc.perform(get("/api/v1/artifacts/{a}", init).with(user("paula")))
			.andExpect(jsonPath("$.status.key").value("IDEA"))
			.andExpect(jsonPath("$.version").value(0));
		mvc.perform(get("/api/v1/artifacts/{a}/history", init).with(user("paula")))
			.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void quemLeMasNaoEditaRecebe403EQuemNaoLe404() throws Exception {
		String init = initiative();
		String feat = JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("tiago"),
				"{\"type\":\"FEATURE\",\"title\":\"F\",\"parentId\":\"" + init + "\",\"orgUnitId\":\"" + cadeia + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/v1/artifacts/{a}/transitions", feat).with(user("paula")))
			.andExpect(jsonPath("$.items[?(@.to.key=='REFINEMENT')].canExecute").value(containsInAnyOrder(false)));
		transition(feat, "paula", "REFINEMENT", 0, null).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/artifacts/{a}/transitions", feat).with(user("carlos"))).andExpect(status().isNotFound());
		transition(feat, "tiago", "REFINEMENT", 0, null).andExpect(status().isOk());
	}

	@Test
	void itemArquivadoNaoMudaDeStatus() throws Exception {
		String init = initiative();
		mvc.perform(delete("/api/v1/artifacts/{a}", init).with(user("paula")).with(csrf())).andExpect(status().isNoContent());
		transition(init, "paula", "BACKLOG", 1, null)
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private ResultActions transition(String artifact, String username, String toKey, int version, String comment)
			throws Exception {
		String options = mvc.perform(get("/api/v1/artifacts/{a}/transitions", artifact).with(user(username)))
			.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(options, "$.items[?(@.to.key=='" + toKey + "')].id");
		String body = "{\"transitionId\":\"" + ids.getFirst() + "\",\"version\":" + version
				+ (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}";
		return mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", artifact), user(username), body));
	}

	private String initiative() throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("paula"),
				"{\"type\":\"INITIATIVE\",\"title\":\"Iniciativa WF\",\"orgUnitId\":\"" + portfolio + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("wf.admin"),
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
