package br.com.aquaqe.platform.board;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/**
 * Templates de quadro (#50; MDL-07): catálogo único com dois prontos por nível; aplicados por cópia ao criar o quadro;
 * quem gere um quadro salva as colunas dele como template; o administrador renomeia e remove, sem mexer nos quadros.
 */
@SpringBootTest(properties = "aquaqe.access.admin-username=bt.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BoardTemplateIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String squad;
	String portfolio;

	@BeforeAll
	void organizacao() throws Exception {
		mvc.perform(get("/api/v1/me").with(user("bt.admin"))).andExpect(status().isOk());
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio BT", "PORTFOLIO", root);
		String cadeia = unit("Cadeia BT", "CADEIA", portfolio);
		squad = unit("Squad BT", "SQUAD", cadeia);
		grant("bt.pm", "PRODUCT_MANAGER", portfolio);
		grant("bt.dev", "SQUAD_MEMBER", portfolio);
	}

	@Test
	void prontosAplicadosPorCopiaESalvarComoTemplate() throws Exception {
		mvc.perform(get("/api/v1/board-templates").param("level", "FL1").with(user("bt.dev")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name", contains("Kanban simples", "Kanban — Sustentação")))
			.andExpect(jsonPath("$.items[*].builtin", contains(true, true)));
		String sustentacao = templateId("FL1", "Kanban — Sustentação");

		String board = JsonPath.read(createBoard(squad, "FL1", sustentacao).andExpect(status().isCreated())
			.andExpect(jsonPath("$.columns[*].name", contains("Triagem", "Em atendimento", "Validando", "Resolvido")))
			.andExpect(jsonPath("$.columns[0].statusKeys", hasItem("OPEN")))
			.andExpect(jsonPath("$.columns[1].wipLimit").value(5))
			.andReturn().getResponse().getContentAsString(), "$.id");
		createBoard(portfolio, "FL3", sustentacao).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("templateId"));
		createBoard(portfolio, "FL3", UUID.randomUUID().toString()).andExpect(status().isBadRequest());

		// Quem gere o quadro salva as colunas como template; nome repetido no nível é recusado.
		mvc.perform(json(patch("/api/v1/boards/{id}", board), user("bt.pm"), """
				{"columns":[
				 {"name":"Fila","phase":"UPSTREAM","statusKeys":["OPEN","BACKLOG","READY","TODO"]},
				 {"name":"Fazendo","phase":"DOWNSTREAM","statusKeys":["INVESTIGATING","FIXING","DEVELOPMENT","IN_PROGRESS",
				  "CODE_REVIEW","TESTING","RESOLVED"],"wipLimit":3,"wipPolicy":"BLOCK"},
				 {"name":"Feito","phase":"DOWNSTREAM","statusKeys":["DONE","CLOSED"]}]}
				"""))
			.andExpect(status().isOk());
		mvc.perform(json(post("/api/v1/boards/{id}/template", board), user("bt.dev"), "{\"name\":\"Do time\"}"))
			.andExpect(status().isForbidden());
		String own = JsonPath.read(mvc.perform(json(post("/api/v1/boards/{id}/template", board), user("bt.pm"),
				"{\"name\":\"Sustentação do time\",\"description\":\"Três colunas com WIP 3\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.level").value("FL1"))
			.andExpect(jsonPath("$.builtin").value(false))
			.andExpect(jsonPath("$.columns[*].name", contains("Fila", "Fazendo", "Feito")))
			.andExpect(jsonPath("$.columns[1].wipPolicy").value("BLOCK"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(json(post("/api/v1/boards/{id}/template", board), user("bt.pm"), "{\"name\":\"sustentação do TIME\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		assertEvents("board_template.created", 1);

		String copia = JsonPath.read(createBoard(squad, "FL1", own).andExpect(status().isCreated())
			.andExpect(jsonPath("$.columns[1].wipLimit").value(3))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// O administrador renomeia e remove; os quadros criados não mudam.
		mvc.perform(json(patch("/api/v1/board-templates/{id}", own), user("bt.pm"), "{\"name\":\"X\"}"))
			.andExpect(status().isForbidden());
		mvc.perform(json(patch("/api/v1/board-templates/{id}", own), user("bt.admin"), "{\"name\":\"Sustentação enxuta\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Sustentação enxuta"));
		mvc.perform(delete("/api/v1/board-templates/{id}", own).with(user("bt.pm")).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/board-templates/{id}", own).with(user("bt.admin")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/board-templates").param("level", "FL1").with(user("bt.dev")))
			.andExpect(jsonPath("$.items[*].name", not(hasItem("Sustentação enxuta"))));
		mvc.perform(get("/api/v1/boards/{id}", copia).with(user("bt.pm")))
			.andExpect(jsonPath("$.columns[*].name", contains("Fila", "Fazendo", "Feito")));
	}

	@Test
	void statusQueNaoValemParaAUnidadeSaoIgnorados() throws Exception {
		// Kanban simples de FL3 tem IDEA...DONE; todos valem no Portfólio. Um template com status estranho perde só eles.
		String template = templateId("FL3", "Funil de Portfólio");
		jdbc.update("""
				update board.board_template set columns = jsonb_set(columns, '{0,statusKeys}', '["IDEA","NAO_EXISTE"]')
				 where id = ?::uuid
				""", template);
		createBoard(portfolio, "FL3", template).andExpect(status().isCreated())
			.andExpect(jsonPath("$.columns[0].statusKeys", contains("IDEA")));
		jdbc.update("""
				update board.board_template set columns = '[{"name":"Nada","phase":"UPSTREAM","statusKeys":["NAO_EXISTE"]}]'
				 where id = ?::uuid
				""", template);
		createBoard(portfolio, "FL3", template).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("templateId"));
	}

	// ---------------------------------------------------------------------------------------------- apoio

	private String templateId(String level, String name) throws Exception {
		String body = mvc.perform(get("/api/v1/board-templates").param("level", level).with(user("bt.admin")))
			.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(body, "$.items[?(@.name == '" + name + "')].id");
		return ids.getFirst();
	}

	private ResultActions createBoard(String unitId, String level, String templateId) throws Exception {
		return mvc.perform(json(post("/api/v1/boards"), user("bt.pm"),
				"{\"orgUnitId\":\"" + unitId + "\",\"level\":\"" + level + "\",\"templateId\":\"" + templateId + "\"}"));
	}

	private void assertEvents(String type, int min) {
		Integer count = jdbc.queryForObject("select count(*) from events.outbox_event where type = ?", Integer.class, type);
		org.assertj.core.api.Assertions.assertThat(count).isGreaterThanOrEqualTo(min);
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("bt.admin"),
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
