package br.com.aquaqe.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

import br.com.aquaqe.platform.events.EventConsumer;
import br.com.aquaqe.platform.events.EventEnvelope;
import br.com.aquaqe.platform.events.OutboxRelay;

/**
 * Garantias da auditoria (#8; REV-03): só inserção, âncoras da cadeia, SLA de atraso e a fila de falhas dos consumidores
 * que não são a auditoria. O repassador e as âncoras são acionados à mão.
 */
@SpringBootTest(properties = { "aquaqe.access.admin-username=ag.admin", "aquaqe.events.relay.enabled=false",
	"aquaqe.audit.anchor.enabled=false" })
@AutoConfigureMockMvc
@Testcontainers
class AuditGuaranteesIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	/** Liga e desliga a falha do consumidor de teste. */
	static final AtomicBoolean FAILING = new AtomicBoolean();

	@TestConfiguration
	static class FlakyConsumerConfiguration {
		@Bean
		EventConsumer flakyConsumer() {
			return new EventConsumer() {
				@Override
				public void accept(EventEnvelope event) {
					if (FAILING.get() && "org_unit.created".equals(event.type()) && event.after() != null
							&& "Portfólio Falho".equals(event.after().get("name"))) {
						throw new IllegalStateException("consumidor de teste fora do ar");
					}
				}

				@Override
				public String name() {
					return "FlakyConsumer";
				}
			};
		}
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OutboxRelay relay;

	@Autowired
	AuditTrail trail;

	@Test
	void aTrilhaSoAceitaInsercao() throws Exception {
		createUnit("Portfólio Imutável");
		relay.drain();
		assertThatThrownBy(() -> jdbc.update("update audit.audit_event set action = 'x'"))
			.isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("só aceita inserção");
		assertThatThrownBy(() -> jdbc.update("delete from audit.audit_event"))
			.isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("só aceita inserção");
		assertThatThrownBy(() -> jdbc.execute("truncate audit.audit_event"))
			.isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("só aceita inserção");
		trail.anchor();
		assertThatThrownBy(() -> jdbc.update("delete from audit.hash_anchor"))
			.isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("só aceita inserção");
	}

	@Test
	void ancorasConferemComATrilha() throws Exception {
		createUnit("Portfólio Ancorado");
		relay.drain();
		Long last = jdbc.queryForObject("select max(sequence) from audit.audit_event", Long.class);
		assertThat(trail.anchor()).isPresent();
		assertThat(trail.anchor()).isEmpty(); // a trilha não cresceu: nada a ancorar
		mvc.perform(get("/api/v1/audit-events/anchors").with(user("ag.leitor"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/audit-events/anchors").with(admin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].sequence").value(hasItem(last.intValue())));
		mvc.perform(get("/api/v1/audit-events/verification").with(admin()))
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.firstAnchorMismatch").doesNotExist());

		// Quem administra o banco troca o hash da âncora: a verificação aponta a âncora.
		jdbc.execute("alter table audit.hash_anchor disable trigger hash_anchor_append_only");
		try {
			jdbc.update("update audit.hash_anchor set hash = repeat('f', 64) where sequence = ?", last);
			mvc.perform(get("/api/v1/audit-events/verification").with(admin()))
				.andExpect(jsonPath("$.valid").value(false))
				.andExpect(jsonPath("$.firstAnchorMismatch").value(last.intValue()));
			jdbc.update("update audit.hash_anchor set hash = (select hash from audit.audit_event where sequence = ?)"
					+ " where sequence = ?", last, last);
		} finally {
			jdbc.execute("alter table audit.hash_anchor enable trigger hash_anchor_append_only");
		}
	}

	@Test
	void situacaoDaEntregaComOSla() throws Exception {
		mvc.perform(get("/api/v1/audit-events/status").with(user("ag.leitor"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/audit-events/status").with(admin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.targetSeconds").value(60))
			.andExpect(jsonPath("$.alertSeconds").value(300))
			.andExpect(jsonPath("$.delayed").value(false));
		mvc.perform(get("/actuator/health"))
			.andExpect(jsonPath("$.components.audit.status").value("UP"));
	}

	@Test
	void falhaDeOutroConsumidorVaiParaAFilaEATrilhaSegue() throws Exception {
		FAILING.set(true);
		try {
			String unit = createUnit("Portfólio Falho");
			// Quatro tentativas param o repassador; na quinta, o evento vai para a fila e a entrega segue.
			for (int i = 0; i < 5; i++) {
				relay.drain();
			}
			mvc.perform(get("/api/v1/audit-events").param("targetId", unit).with(admin()))
				.andExpect(jsonPath("$.items[*].action").value(hasItem("org_unit.created")));
			String list = mvc.perform(get("/api/v1/admin/event-failures").with(admin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].consumer").value("FlakyConsumer"))
				.andExpect(jsonPath("$.items[0].type").value("org_unit.created"))
				.andExpect(jsonPath("$.items[0].attempts").value(5))
				.andReturn().getResponse().getContentAsString();
			String failure = JsonPath.read(list, "$.items[0].id");
			mvc.perform(get("/api/v1/audit-events/status").with(admin())).andExpect(jsonPath("$.deadLetters").value(1));
			mvc.perform(get("/api/v1/admin/event-failures").with(user("ag.leitor"))).andExpect(status().isForbidden());

			// Os eventos seguintes não esperam pela fila.
			createUnit("Portfólio Depois Da Fila");
			assertThat(relay.drain()).isGreaterThanOrEqualTo(1);

			// Reprocessar com o consumidor ainda fora do ar: 409, e a falha continua.
			mvc.perform(post("/api/v1/admin/event-failures/{id}/retry", failure).with(admin()).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RETRY_FAILED"));
			FAILING.set(false);
			mvc.perform(post("/api/v1/admin/event-failures/{id}/retry", failure).with(admin()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.consumer").value("FlakyConsumer"));
			mvc.perform(get("/api/v1/admin/event-failures").with(admin())).andExpect(jsonPath("$.items.length()").value(0));
			mvc.perform(post("/api/v1/admin/event-failures/{id}/retry", failure).with(admin()).with(csrf()))
				.andExpect(status().isNotFound());
		} finally {
			FAILING.set(false);
		}
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private String createUnit(String name) throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class)
			.toString();
		return JsonPath.read(mvc.perform(post("/api/v1/org-units").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"level\":\"PORTFOLIO\",\"parentId\":\"" + root + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static RequestPostProcessor admin() {
		return user("ag.admin");
	}

	private static RequestPostProcessor user(String username) {
		return jwt().jwt(token -> token.subject("sub-" + username).claim("preferred_username", username)
			.claim("name", username));
	}
}
