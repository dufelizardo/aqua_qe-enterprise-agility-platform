package br.com.aquaqe.platform.notification;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import br.com.aquaqe.platform.events.OutboxRelay;

/** Comentários com @menção, notificações e favoritos nas páginas do Wiki (#85; Proposta 07, Fase D). */
@SpringBootTest(properties = { "aquaqe.access.admin-username=co.admin", "aquaqe.events.relay.enabled=false" })
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentsIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OutboxRelay relay;

	String portfolio;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio CO", "PORTFOLIO", root);
		String cadeia = unit("Cadeia CO", "CADEIA", portfolio);
		String outro = unit("Outro Portfólio CO", "PORTFOLIO", root);
		grant("paula", "PRODUCT_MANAGER", portfolio);
		grant("tiago", "TECH_LEAD", portfolio);
		grant("sara", "QA_ENGINEER", cadeia);
		grant("otavio", "PRODUCT_MANAGER", outro);
		grant("rui", "QA_ENGINEER", portfolio);
	}

	@Test
	void comentarMencionarResponderENotificar() throws Exception {
		String page = page("Guia CO");
		String tiago = id("tiago");
		String sara = id("sara");

		mvc.perform(get("/api/v1/pages/{id}/people", page).param("q", "SA").with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].name").value(contains("sara")));
		mvc.perform(get("/api/v1/pages/{id}/people", page).with(user("paula")))
			.andExpect(jsonPath("$.items[*].name").value(not(hasItem("otavio"))));

		String first = JsonPath.read(comment("paula", page, "{\"body\":\"@tiago @sara revisem, por favor\","
				+ "\"quote\":\"checklist de segurança\",\"mentionIds\":[\"" + tiago + "\",\"" + sara + "\"]}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.quote").value("checklist de segurança"))
			.andExpect(jsonPath("$.mentions[*].name").value(contains("tiago", "sara")))
			.andExpect(jsonPath("$.canDelete").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");
		comment("paula", page, "{\"body\":\"oi\",\"mentionIds\":[\"" + id("otavio") + "\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("mentionIds"));
		comment("tiago", page, "{\"body\":\"Revisado.\",\"parentId\":\"" + first + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parentId").value(first));
		comment("otavio", page, "{\"body\":\"não leio\"}").andExpect(status().isNotFound());

		relay.drain();
		mvc.perform(get("/api/v1/me/notifications").with(user("tiago")))
			.andExpect(jsonPath("$.unread").value(1))
			.andExpect(jsonPath("$.items[0].kind").value("MENTION"))
			.andExpect(jsonPath("$.items[0].actor.name").value("paula"))
			.andExpect(jsonPath("$.items[0].title").value("Guia CO"))
			.andExpect(jsonPath("$.items[0].targetId").value(page));
		mvc.perform(get("/api/v1/me/notifications").with(user("sara")))
			.andExpect(jsonPath("$.items[0].kind").value("MENTION"));
		String reply = JsonPath.read(mvc.perform(get("/api/v1/me/notifications").with(user("paula")))
			.andExpect(jsonPath("$.unread").value(1))
			.andExpect(jsonPath("$.items[0].kind").value("REPLY"))
			.andExpect(jsonPath("$.items[0].actor.name").value("tiago"))
			.andReturn().getResponse().getContentAsString(), "$.items[0].id");
		mvc.perform(post("/api/v1/me/notifications/{id}/read", reply).with(user("tiago")).with(csrf()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/me/notifications/{id}/read", reply).with(user("paula")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/me/notifications").with(user("paula"))).andExpect(jsonPath("$.unread").value(0));
		mvc.perform(post("/api/v1/me/notifications/read-all").with(user("tiago")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/me/notifications").with(user("tiago"))).andExpect(jsonPath("$.unread").value(0));

		// Só o autor exclui; com resposta, o comentário fica como excluído.
		mvc.perform(delete("/api/v1/pages/{id}/comments/{c}", page, first).with(user("tiago")).with(csrf()))
			.andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/pages/{id}/comments/{c}", page, first).with(user("paula")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/pages/{id}/comments", page).with(user("sara")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].deleted").value(true))
			.andExpect(jsonPath("$.items[0].body").doesNotExist())
			.andExpect(jsonPath("$.items[1].body").value("Revisado."))
			.andExpect(jsonPath("$.items[1].canDelete").value(false));
		mvc.perform(get("/api/v1/pages/{id}/comments", page).with(user("otavio"))).andExpect(status().isNotFound());
	}

	@Test
	void favoritosDaPessoa() throws Exception {
		String page = page("Favorita CO");
		mvc.perform(put("/api/v1/me/favorites/pages/{id}", page).with(user("tiago")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(put("/api/v1/me/favorites/pages/{id}", page).with(user("tiago")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/me/favorites").with(user("tiago")))
			.andExpect(jsonPath("$.items[*].title").value(hasItem("Favorita CO")))
			.andExpect(jsonPath("$.items[0].orgUnit.name").value("Portfólio CO"));
		mvc.perform(put("/api/v1/me/favorites/pages/{id}", page).with(user("otavio")).with(csrf()))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/me/favorites/pages/{id}", page).with(user("tiago")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/me/favorites").with(user("tiago")))
			.andExpect(jsonPath("$.items[*].title").value(not(hasItem("Favorita CO"))));
	}

	@Test
	void preferenciasDoUsuarioEAvisos() throws Exception {
		mvc.perform(get("/api/v1/me/preferences").with(user("rui")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("SYSTEM"))
			.andExpect(jsonPath("$.notifications.mention").value(true))
			.andExpect(jsonPath("$.notifications.reply").value(true));
		mvc.perform(json(put("/api/v1/me/preferences"), user("rui"), "{\"theme\":\"ROXO\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("theme"));
		mvc.perform(json(put("/api/v1/me/preferences"), user("rui"),
				"{\"theme\":\"DARK\",\"notifications\":{\"mention\":false,\"reply\":true}}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("DARK"))
			.andExpect(jsonPath("$.notifications.mention").value(false));
		mvc.perform(get("/api/v1/me/preferences").with(user("rui")))
			.andExpect(jsonPath("$.theme").value("DARK"))
			.andExpect(jsonPath("$.notifications.reply").value(true));
		// Cada pessoa tem as suas: as de rui não mudam as de paula.
		mvc.perform(get("/api/v1/me/preferences").with(user("paula"))).andExpect(jsonPath("$.theme").value("SYSTEM"));

		// Menção desligada: rui não recebe; resposta ligada: recebe.
		String page = page("Preferências CO");
		String own = JsonPath.read(comment("rui", page, "{\"body\":\"Dúvida.\"}").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
		comment("paula", page, "{\"body\":\"@rui veja\",\"mentionIds\":[\"" + id("rui") + "\"]}")
			.andExpect(status().isCreated());
		comment("paula", page, "{\"body\":\"Respondido.\",\"parentId\":\"" + own + "\"}")
			.andExpect(status().isCreated());
		relay.drain();
		mvc.perform(get("/api/v1/me/notifications").with(user("rui")))
			.andExpect(jsonPath("$.unread").value(1))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].kind").value("REPLY"));
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private ResultActions comment(String username, String page, String body) throws Exception {
		return mvc.perform(json(post("/api/v1/pages/{id}/comments", page), user(username), body));
	}

	private String page(String title) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units/{u}/pages", portfolio), user("paula"),
				"{\"title\":\"" + title + "\"}")).andExpect(status().isCreated()).andReturn().getResponse()
			.getContentAsString(), "$.id");
	}

	private String id(String username) throws Exception {
		mvc.perform(get("/api/v1/me").with(user(username))).andExpect(status().isOk());
		return jdbc.queryForObject("select id::text from identity.user_account where username = ?", String.class, username);
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("co.admin"),
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
