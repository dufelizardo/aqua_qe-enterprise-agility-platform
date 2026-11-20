package br.com.aquaqe.platform.board;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** Sprints opcionais por squad (SSD 3.9; PRD-RF-015) e ordenação dos itens (SSD 3.4, rank). */
@SpringBootTest(properties = "aquaqe.access.admin-username=sp.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SprintIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String cadeia;
	String squad;
	String outraSquad;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		String portfolio = unit("Portfólio SP", "PORTFOLIO", root);
		cadeia = unit("Cadeia SP", "CADEIA", portfolio);
		squad = unit("Squad Pix", "SQUAD", cadeia);
		outraSquad = unit("Squad Cartões", "SQUAD", cadeia);
		grant("tiago", "TECH_LEAD", cadeia);
		grant("sergio", "SQUAD_MEMBER", squad);
	}

	@Test
	void criarSprintSoEmSquadEParaQuemGereQuadros() throws Exception {
		createSprint("tiago", squad, "Sprint 1").andExpect(status().isCreated())
			.andExpect(jsonPath("$.state").value("PLANNED"))
			.andExpect(jsonPath("$.itemCount").value(0));
		createSprint("tiago", cadeia, "Errada").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("squadId"));
		createSprint("sergio", squad, "Sem permissão").andExpect(status().isForbidden());
	}

	@Test
	void itensDaPropriaSquadEmUmaSprintAbertaPorVez() throws Exception {
		String sprint = id(createSprint("tiago", squad, "Sprint Itens"));
		String outra = id(createSprint("tiago", squad, "Sprint Outra"));
		String task = task(squad, "Task da squad");
		String alheia = task(outraSquad, "Task de outra squad");

		addItem(sprint, task).andExpect(status().isNoContent());
		addItem(sprint, alheia).andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("SPRINT_ITEM_NOT_ALLOWED"));
		addItem(outra, task).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ITEM_IN_ANOTHER_SPRINT"));
		mvc.perform(get("/api/v1/sprints/{id}/items", sprint).with(user("sergio")))
			.andExpect(jsonPath("$.items[*].id").value(contains(task)));
	}

	@Test
	void iniciarEncerrarDevolveAoBacklogOsNaoConcluidos() throws Exception {
		String squadCiclo = unit("Squad Ciclo", "SQUAD", cadeia);
		String sprint = id(createSprint("tiago", squadCiclo, "Sprint Ciclo"));
		String feita = task(squadCiclo, "Feita");
		String pendente = task(squadCiclo, "Pendente");
		addItem(sprint, feita).andExpect(status().isNoContent());
		addItem(sprint, pendente).andExpect(status().isNoContent());

		action(sprint, "start").andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"));
		String segunda = id(createSprint("tiago", squadCiclo, "Sprint Seguinte"));
		action(segunda, "start").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SPRINT_ALREADY_ACTIVE"));

		// Filtro do quadro pela sprint.
		String board = JsonPath.read(mvc.perform(json(post("/api/v1/boards"), user("tiago"),
				"{\"orgUnitId\":\"" + squadCiclo + "\",\"level\":\"FL1\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(get("/api/v1/boards/{id}/view", board).param("sprintId", sprint).with(user("tiago")))
			.andExpect(jsonPath("$.columns[*].cards[*].id").value(containsInAnyOrder(feita, pendente)));

		moveTo(feita, "IN_PROGRESS", 0);
		moveTo(feita, "DONE", 1);
		action(sprint, "close").andExpect(status().isOk())
			.andExpect(jsonPath("$.sprint.state").value("CLOSED"))
			.andExpect(jsonPath("$.returnedToBacklog").value(1));
		mvc.perform(get("/api/v1/sprints/{id}/items", sprint).with(user("tiago")))
			.andExpect(jsonPath("$.items[*].id").value(contains(feita)));
		mvc.perform(json(patch("/api/v1/sprints/{id}", sprint), user("tiago"), "{\"name\":\"x\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SPRINT_STATE"));
		// O pendente pode entrar na próxima sprint.
		addItem(segunda, pendente).andExpect(status().isNoContent());
	}

	@Test
	void reordenarPoeOItemAntesDeOutro() throws Exception {
		String squadRank = unit("Squad Rank", "SQUAD", cadeia);
		String a = task(squadRank, "A");
		String b = task(squadRank, "B");
		String c = task(squadRank, "C");
		mvc.perform(json(post("/api/v1/artifacts/{a}/rank", c), user("tiago"), "{\"beforeId\":\"" + a + "\"}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/artifacts").header("X-Workspace-Id", squadRank).param("sort", "rank").with(user("tiago")))
			.andExpect(jsonPath("$.items[*].id").value(contains(c, a, b)));
		mvc.perform(json(post("/api/v1/artifacts/{a}/rank", a), user("tiago"), "{\"afterId\":\"" + b + "\"}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/v1/artifacts").header("X-Workspace-Id", squadRank).param("sort", "rank").with(user("tiago")))
			.andExpect(jsonPath("$.items[*].id").value(contains(c, b, a)));
		mvc.perform(json(post("/api/v1/artifacts/{a}/rank", a), user("tiago"), "{}"))
			.andExpect(status().isBadRequest());
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private ResultActions createSprint(String username, String squadId, String name) throws Exception {
		return mvc.perform(json(post("/api/v1/sprints"), user(username),
				"{\"squadId\":\"" + squadId + "\",\"name\":\"" + name + "\",\"goal\":\"Entregar\"}"));
	}

	private ResultActions addItem(String sprint, String artifact) throws Exception {
		return mvc.perform(put("/api/v1/sprints/{id}/items/{a}", sprint, artifact).with(user("tiago")).with(csrf()));
	}

	private ResultActions action(String sprint, String action) throws Exception {
		return mvc.perform(post("/api/v1/sprints/{id}/" + action, sprint).with(user("tiago")).with(csrf()));
	}

	private void moveTo(String artifact, String toKey, int version) throws Exception {
		String options = mvc.perform(get("/api/v1/artifacts/{a}/transitions", artifact).with(user("tiago")))
			.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(options, "$.items[?(@.to.key=='" + toKey + "')].id");
		mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", artifact), user("tiago"),
				"{\"transitionId\":\"" + ids.getFirst() + "\",\"version\":" + version + "}"))
			.andExpect(status().isOk());
	}

	private String task(String squadId, String title) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), user("tiago"),
				"{\"type\":\"TASK\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + squadId + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("sp.admin"),
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
