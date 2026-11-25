package br.com.aquaqe.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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

import br.com.aquaqe.platform.events.OutboxRelay;

/** Outbox e trilha de auditoria (SAD 7.4 e 7.5; SSD 3.10 e 9; REV-03). O repassador é acionado à mão. */
@SpringBootTest(properties = { "aquaqe.access.admin-username=au.admin", "aquaqe.events.relay.enabled=false" })
@AutoConfigureMockMvc
@Testcontainers
class AuditIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OutboxRelay relay;

	@Test
	void operacoesGeramEventosQueViramRegistrosEncadeados() throws Exception {
		String unit = createUnit("Portfólio Auditado", "corr-123");
		mvc.perform(json(patch("/api/v1/org-units/{id}", unit), admin(), "{\"name\":\"Portfólio Renomeado\",\"version\":0}"))
			.andExpect(status().isOk());
		String init = JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), admin(),
				"{\"type\":\"INITIATIVE\",\"title\":\"Auditada\",\"orgUnitId\":\"" + unit + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String options = mvc.perform(get("/api/v1/artifacts/{a}/transitions", init).with(admin()))
			.andReturn().getResponse().getContentAsString();
		List<String> toBacklog = JsonPath.read(options, "$.items[?(@.to.key=='BACKLOG')].id");
		mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", init), admin(),
				"{\"transitionId\":\"" + toBacklog.getFirst() + "\",\"version\":0}"))
			.andExpect(status().isOk());

		// Nada na trilha antes da entrega: a auditoria é assíncrona (ADR-07).
		assertThat(relay.drain()).isGreaterThanOrEqualTo(4);

		mvc.perform(get("/api/v1/audit-events").param("targetId", init).with(admin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].action").value("artifact.status_changed"))
			.andExpect(jsonPath("$.items[0].before.status").value("IDEA"))
			.andExpect(jsonPath("$.items[0].after.status").value("BACKLOG"))
			.andExpect(jsonPath("$.items[0].actor.name").value("au.admin"))
			.andExpect(jsonPath("$.items[1].action").value("artifact.created"));
		mvc.perform(get("/api/v1/audit-events").param("targetId", unit).with(admin()))
			.andExpect(jsonPath("$.items[*].action").value(hasItem("org_unit.created")))
			.andExpect(jsonPath("$.items[?(@.action=='org_unit.updated')].after.name").value(hasItem("Portfólio Renomeado")))
			.andExpect(jsonPath("$.items[?(@.action=='org_unit.created')].correlationId").value(hasItem("corr-123")));
		mvc.perform(get("/api/v1/audit-events/verification").with(admin()))
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.firstInconsistent").doesNotExist());
	}

	@Test
	void adulterarUmRegistroQuebraACadeia() throws Exception {
		createUnit("Portfólio Para Adulterar", null);
		relay.drain();
		Long target = jdbc.queryForObject("select max(sequence) from audit.audit_event", Long.class);
		jdbc.update("update audit.audit_event set action = 'adulterado' where sequence = ?", target);
		mvc.perform(get("/api/v1/audit-events/verification").with(admin()))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.firstInconsistent").value(target));
		// Desfaz, para não afetar os outros testes da classe.
		jdbc.update("update audit.audit_event set action = 'org_unit.created' where sequence = ?", target);
	}

	/** A chave do alvo de uma página é o título (até 200): com 40, a página longa travava a entrega (V13). */
	@Test
	void paginaComTituloLongoEntraNaTrilha() throws Exception {
		String unit = createUnit("Portfólio Título Longo", null);
		String title = "Central de estratégia e Governança Corporativa da Unidade de Negócio Digital";
		String page = JsonPath.read(mvc.perform(json(post("/api/v1/org-units/{u}/pages", unit), admin(),
				"{\"title\":\"" + title + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		relay.drain();
		mvc.perform(get("/api/v1/audit-events").param("targetId", page).with(admin()))
			.andExpect(jsonPath("$.items[0].action").value("knowledge_page.created"))
			.andExpect(jsonPath("$.items[0].target.key").value(title));
	}

	@Test
	void reentregaNaoDuplicaRegistro() throws Exception {
		createUnit("Portfólio Reentregue", null);
		relay.drain();
		UUID eventId = jdbc.queryForObject("select event_id from audit.audit_event order by sequence desc limit 1", UUID.class);
		long before = jdbc.queryForObject("select count(*) from audit.audit_event", Long.class);
		jdbc.update("update events.outbox_event set published_at = null where event_id = ?", eventId);
		relay.drain();
		assertThat(jdbc.queryForObject("select count(*) from audit.audit_event", Long.class)).isEqualTo(before);
	}

	@Test
	void transicaoBloqueadaFicaRegistradaMesmoComARespostaDeErro() throws Exception {
		String unit = createUnit("Portfólio Bloqueio", null);
		String board = JsonPath.read(mvc.perform(json(post("/api/v1/boards"), admin(),
				"{\"orgUnitId\":\"" + unit + "\",\"level\":\"FL3\"}"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(json(patch("/api/v1/boards/{id}", board), admin(), """
				{"columns":[{"name":"Ideia","phase":"UPSTREAM","statusKeys":["IDEA"]},
				 {"name":"Backlog","phase":"UPSTREAM","statusKeys":["BACKLOG"],"wipLimit":1,"wipPolicy":"BLOCK"},
				 {"name":"Resto","phase":"DOWNSTREAM","statusKeys":["FEASIBILITY","REFINEMENT","BUILDING","DONE"]}]}
				"""))
			.andExpect(status().isOk());
		String first = initiative(unit);
		String second = initiative(unit);
		move(first, "BACKLOG").andExpect(status().isOk());
		move(second, "BACKLOG").andExpect(status().is(422));
		relay.drain();
		mvc.perform(get("/api/v1/audit-events").param("targetId", second).param("action", "artifact.transition_blocked")
				.with(admin()))
			.andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void soQuemConsultaAuditoriaLeATrilha() throws Exception {
		mvc.perform(get("/api/v1/me").with(user("curioso"))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/audit-events").with(user("curioso")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.correlationId").isNotEmpty());
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private String createUnit(String name, String correlationId) throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		MockHttpServletRequestBuilder request = json(post("/api/v1/org-units"), admin(),
			"{\"name\":\"" + name + "\",\"level\":\"PORTFOLIO\",\"parentId\":\"" + root + "\"}");
		if (correlationId != null) {
			request.header("X-Correlation-Id", correlationId);
		}
		var result = mvc.perform(request).andExpect(status().isCreated());
		if (correlationId != null) {
			result.andExpect(header().string("X-Correlation-Id", correlationId));
		}
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String initiative(String unit) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/artifacts"), admin(),
				"{\"type\":\"INITIATIVE\",\"title\":\"I\",\"orgUnitId\":\"" + unit + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private org.springframework.test.web.servlet.ResultActions move(String artifact, String toKey) throws Exception {
		String options = mvc.perform(get("/api/v1/artifacts/{a}/transitions", artifact).with(admin()))
			.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(options, "$.items[?(@.to.key=='" + toKey + "')].id");
		return mvc.perform(json(post("/api/v1/artifacts/{a}/transitions", artifact), admin(),
				"{\"transitionId\":\"" + ids.getFirst() + "\",\"version\":0}"));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, RequestPostProcessor user,
			String body) {
		return request.with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static RequestPostProcessor admin() {
		return user("au.admin");
	}

	private static RequestPostProcessor user(String username) {
		return jwt().jwt(token -> token.subject("sub-" + username).claim("preferred_username", username)
			.claim("name", username));
	}
}
