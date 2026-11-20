package br.com.aquaqe.platform.board;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

/** Quadros (SSD 3.9) e workspaces do usuário (SSD 3.1). */
@SpringBootTest(properties = "aquaqe.access.admin-username=bd.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoardIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;
	String outroPortfolio;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio Quadro", "PORTFOLIO", root);
		outroPortfolio = unit("Portfólio Vizinho", "PORTFOLIO", root);
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("sergio", "SQUAD_MEMBER", portfolio);
		grant("carlos", "PRODUCT_MANAGER", outroPortfolio);
	}

	@Test
	void workspacesSaoAsUnidadesComPapel() throws Exception {
		grant("wanda", "PRODUCT_MANAGER", portfolio);
		grant("wanda", "BUSINESS_OWNER", portfolio);
		mvc.perform(get("/api/v1/me/workspaces").with(user("wanda")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(portfolio))
			.andExpect(jsonPath("$.items[0].level").value("PORTFOLIO"))
			.andExpect(jsonPath("$.items[0].roles").value(hasItem("BUSINESS_OWNER")));
	}

	@Test
	void quadroDePortfolioComColunasDoWorkflowECartoesNoStatus() throws Exception {
		String board = createBoard("paula", portfolio, "FL3");
		mvc.perform(get("/api/v1/boards/{id}", board).with(user("paula")))
			.andExpect(jsonPath("$.columns[*].statusKeys[0]")
				.value(contains("IDEA", "BACKLOG", "FEASIBILITY", "REFINEMENT", "BUILDING", "DONE")))
			.andExpect(jsonPath("$.columns[*].phase")
				.value(contains("UPSTREAM", "UPSTREAM", "UPSTREAM", "UPSTREAM", "DOWNSTREAM", "DOWNSTREAM")));

		String init = JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("paula"),
				"{\"type\":\"INITIATIVE\",\"title\":\"No quadro\",\"orgUnitId\":\"" + portfolio + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");

		mvc.perform(get("/api/v1/boards/{id}/view", board).with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.columns[0].cards[*].key").value(hasItem(init)));

		String options = mvc.perform(get("/api/v1/artifacts/{a}/transitions", init).with(user("paula")))
			.andReturn().getResponse().getContentAsString();
		List<String> toBacklog = JsonPath.read(options, "$.items[?(@.to.key=='BACKLOG')].id");
		mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", init), user("paula"),
				"{\"transitionId\":\"" + toBacklog.getFirst() + "\",\"version\":0}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/boards/{id}/view", board).with(user("paula")))
			.andExpect(jsonPath("$.columns[1].cards[*].key").value(hasItem(init)));

		// Quem não lê os itens da unidade vê o quadro vazio (SSD 2.1, regra 6).
		mvc.perform(get("/api/v1/boards/{id}/view", board).with(user("carlos")))
			.andExpect(jsonPath("$.columns[0].cards.length()").value(0))
			.andExpect(jsonPath("$.columns[1].cards.length()").value(0));
	}

	@Test
	void quemNaoGereQuadrosRecebe403() throws Exception {
		mvc.perform(json(post("/api/v1/boards"), user("sergio"),
				"{\"orgUnitId\":\"" + portfolio + "\",\"level\":\"FL3\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void nivelInvalidoRecebe400() throws Exception {
		mvc.perform(json(post("/api/v1/boards"), user("paula"), "{\"orgUnitId\":\"" + portfolio + "\",\"level\":\"FL9\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("level"));
	}

	@Test
	void listagemFiltraPorUnidadeENivel() throws Exception {
		String board = createBoard("carlos", outroPortfolio, "FL3");
		mvc.perform(get("/api/v1/boards").param("orgUnitId", outroPortfolio).param("level", "FL3").with(user("carlos")))
			.andExpect(jsonPath("$.items[*].id").value(hasItem(board)));
		mvc.perform(get("/api/v1/boards").param("orgUnitId", outroPortfolio).param("level", "FL2").with(user("carlos")))
			.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void configurarColunasValidaFasesEStatus() throws Exception {
		String unit = unit("Portfólio Config", "PORTFOLIO", root());
		grant("paula", "PRODUCT_MANAGER", unit);
		String board = createBoard("paula", unit, "FL3");
		// Agrupa Ideia e Backlog numa coluna; Estudo de Viabilidade fica sem coluna (aviso).
		mvc.perform(json(patch("/api/v1/boards/{id}", board), user("paula"), """
				{"name":"Portfólio Configurado","columns":[
				  {"name":"Entrada","phase":"UPSTREAM","statusKeys":["IDEA","BACKLOG"],"wipLimit":5},
				  {"name":"Refinamento","phase":"UPSTREAM","statusKeys":["REFINEMENT"],"wipLimit":2,"wipPolicy":"BLOCK"},
				  {"name":"Construção","phase":"DOWNSTREAM","statusKeys":["BUILDING"]},
				  {"name":"Concluído","phase":"DOWNSTREAM","statusKeys":["DONE"]}]}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Portfólio Configurado"))
			.andExpect(jsonPath("$.columns.length()").value(4))
			.andExpect(jsonPath("$.columns[0].statusKeys").value(contains("IDEA", "BACKLOG")))
			.andExpect(jsonPath("$.columns[1].wipPolicy").value("BLOCK"))
			.andExpect(jsonPath("$.columns[0].wipPolicy").value("WARN"))
			.andExpect(jsonPath("$.unmappedStatusKeys").value(contains("FEASIBILITY")));

		mvc.perform(json(patch("/api/v1/boards/{id}", board), user("paula"), """
				{"columns":[{"name":"A","phase":"UPSTREAM","statusKeys":["IDEA"]},{"name":"B","phase":"UPSTREAM","statusKeys":["IDEA"]}]}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("columns[1].statusKeys"));
		mvc.perform(json(patch("/api/v1/boards/{id}", board), user("paula"), """
				{"columns":[{"name":"A","phase":"DOWNSTREAM","statusKeys":["DONE"]},{"name":"B","phase":"UPSTREAM","statusKeys":["IDEA"]}]}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("columns[1].phase"));
		mvc.perform(json(patch("/api/v1/boards/{id}", board), user("paula"), """
				{"columns":[{"name":"A","phase":"UPSTREAM","statusKeys":["DEVELOPMENT"]}]}
				"""))
			.andExpect(status().isBadRequest());
		mvc.perform(json(patch("/api/v1/boards/{id}", board), user("sergio"), "{\"name\":\"x\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void limiteDeWipAvisaOuImpede() throws Exception {
		String unit = unit("Portfólio WIP", "PORTFOLIO", root());
		grant("paula", "PRODUCT_MANAGER", unit);
		String board = createBoard("paula", unit, "FL3");
		mvc.perform(json(patch("/api/v1/boards/{id}", board), user("paula"), """
				{"columns":[
				  {"name":"Ideia","phase":"UPSTREAM","statusKeys":["IDEA"],"wipLimit":2},
				  {"name":"Backlog","phase":"UPSTREAM","statusKeys":["BACKLOG"],"wipLimit":1,"wipPolicy":"BLOCK"},
				  {"name":"Resto","phase":"DOWNSTREAM","statusKeys":["FEASIBILITY","REFINEMENT","BUILDING","DONE"]}]}
				"""))
			.andExpect(status().isOk());

		String a = initiative("paula", unit);
		mvc.perform(get("/api/v1/boards/{id}/view", board).with(user("paula")))
			.andExpect(jsonPath("$.columns[0].wipState").value("UNDER"))
			.andExpect(jsonPath("$.columns[2].wipState").value("NONE"));
		String b = initiative("paula", unit);
		mvc.perform(get("/api/v1/boards/{id}/view", board).with(user("paula")))
			.andExpect(jsonPath("$.columns[0].wipCount").value(2))
			.andExpect(jsonPath("$.columns[0].wipState").value("AT"));
		// WARN: a terceira entra e a coluna passa do limite.
		initiative("paula", unit);
		mvc.perform(get("/api/v1/boards/{id}/view", board).with(user("paula")))
			.andExpect(jsonPath("$.columns[0].wipState").value("OVER"));

		// BLOCK: Backlog aceita uma; a segunda é recusada pelo guarda, com o motivo.
		moveTo(a, "BACKLOG", 0).andExpect(status().isOk());
		moveTo(b, "BACKLOG", 0)
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("GATE_BLOCKED"))
			.andExpect(jsonPath("$.failedRules[0].type").value("WIP_LIMIT"));
		mvc.perform(get("/api/v1/artifacts/{a}/transitions", b).with(user("paula")))
			.andExpect(jsonPath("$.items[?(@.to.key=='BACKLOG')].failedRules[0].type").value(hasItem("WIP_LIMIT")));
	}

	@Test
	void marcaDeBloqueadoAparecemNoCartaoEFiltra() throws Exception {
		String unit = unit("Portfólio Bloqueio", "PORTFOLIO", root());
		grant("paula", "PRODUCT_MANAGER", unit);
		String board = createBoard("paula", unit, "FL3");
		String init = initiative("paula", unit);
		initiative("paula", unit);
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"),
				"{\"blocked\":true,\"blockedReason\":\"Aguardando o jurídico\",\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.blocked").value(true))
			.andExpect(jsonPath("$.blockedReason").value("Aguardando o jurídico"));
		mvc.perform(get("/api/v1/boards/{id}/view", board).param("blocked", "true").with(user("paula")))
			.andExpect(jsonPath("$.columns[0].cards.length()").value(1))
			.andExpect(jsonPath("$.columns[0].cards[0].blockedReason").value("Aguardando o jurídico"));
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"blocked\":false,\"version\":1}"))
			.andExpect(jsonPath("$.blocked").value(false))
			.andExpect(jsonPath("$.blockedReason").doesNotExist());
	}

	private org.springframework.test.web.servlet.ResultActions moveTo(String artifact, String toKey, int version)
			throws Exception {
		String options = mvc.perform(get("/api/v1/artifacts/{a}/transitions", artifact).with(user("paula")))
			.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(options, "$.items[?(@.to.key=='" + toKey + "')].id");
		return mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", artifact), user("paula"),
				"{\"transitionId\":\"" + ids.getFirst() + "\",\"version\":" + version + "}"));
	}

	private String initiative(String username, String unitId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user(username),
				"{\"type\":\"INITIATIVE\",\"title\":\"Iniciativa\",\"orgUnitId\":\"" + unitId + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String root() {
		return jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
	}

	private String createBoard(String username, String unitId, String level) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/boards"), user(username),
				"{\"orgUnitId\":\"" + unitId + "\",\"level\":\"" + level + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("bd.admin"),
				"{\"name\":\"" + name + "\",\"level\":\"" + level + "\",\"parentId\":\"" + parentId + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private void grant(String username, String role, String orgUnitId) throws Exception {
		mvc.perform(get("/api/v1/me").with(user(username))).andExpect(status().isOk());
		jdbc.update("""
				insert into access.role_assignment (user_id, role_id, org_unit_id)
				select u.id, r.id, ?::uuid from identity.user_account u, access.role r
				 where u.username = ? and r.key = ?
				on conflict do nothing
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
