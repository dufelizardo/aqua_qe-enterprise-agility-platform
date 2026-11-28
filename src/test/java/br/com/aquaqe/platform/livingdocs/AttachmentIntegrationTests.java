package br.com.aquaqe.platform.livingdocs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/** Anexos dos artefatos (SSD 3.7; #13, REV-08). Limite reduzido a 1 KB para testar o 413. */
@SpringBootTest(properties = { "aquaqe.access.admin-username=at.admin", "aquaqe.attachments.max-size=1KB" })
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttachmentIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio AT", "PORTFOLIO", root);
		String outro = unit("Outro Portfólio AT", "PORTFOLIO", root);
		grant("ana", "PRODUCT_MANAGER", portfolio);
		grant("leo", "VIEWER", portfolio);
		grant("oto", "PRODUCT_MANAGER", outro);
	}

	@Test
	void enviarListarBaixarERemover() throws Exception {
		String init = artifact("Com anexo");
		String id = JsonPath.read(upload("ana", init, "C:\\docs\\plano.txt", "text/plain; charset=utf-8", "olá")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.fileName").value("plano.txt"))
			.andExpect(jsonPath("$.contentType").value("text/plain"))
			.andExpect(jsonPath("$.size").value(4))
			.andExpect(jsonPath("$.uploadedBy.name").value("ana"))
			.andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/v1/artifacts/{a}/attachments", init).with(user("leo")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canEdit").value(false))
			.andExpect(jsonPath("$.maxSize").value(1024))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].fileName").value("plano.txt"));
		mvc.perform(get("/api/v1/artifacts/{a}/attachments", init).with(user("ana")))
			.andExpect(jsonPath("$.canEdit").value(true));

		mvc.perform(get("/api/v1/attachments/{id}/content", id).with(user("leo")))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(content().bytes("olá".getBytes(StandardCharsets.UTF_8)));
		mvc.perform(get("/api/v1/attachments/{id}/content", id).with(user("oto"))).andExpect(status().isNotFound());

		mvc.perform(delete("/api/v1/attachments/{id}", id).with(user("leo")).with(csrf())).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/attachments/{id}", id).with(user("ana")).with(csrf())).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/artifacts/{a}/attachments", init).with(user("ana")))
			.andExpect(jsonPath("$.items", hasSize(0)));
		mvc.perform(get("/api/v1/attachments/{id}/content", id).with(user("ana"))).andExpect(status().isNotFound());

		Integer events = jdbc.queryForObject(
			"select count(*) from events.outbox_event where type in ('attachment.uploaded', 'attachment.removed')"
					+ " and envelope -> 'aggregate' ->> 'id' = ?", Integer.class, id);
		org.junit.jupiter.api.Assertions.assertEquals(2, events);
	}

	@Test
	void leitorNaoEnviaEOsLimitesValem() throws Exception {
		String init = artifact("Limites");
		upload("leo", init, "a.txt", "text/plain", "x").andExpect(status().isForbidden());
		upload("oto", init, "a.txt", "text/plain", "x").andExpect(status().isNotFound());
		upload("ana", init, "vazio.txt", "text/plain", "")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("file"));
		upload("ana", init, "grande.bin", "application/octet-stream", "x".repeat(2048))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("ATTACHMENT_TOO_LARGE"));
		upload("ana", init, "pagina.html", "text/html", "<script>1</script>").andExpect(status().isCreated());
		upload("ana", init, "estranho.dat", "nao é tipo", "x")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.contentType").value("application/octet-stream"));
	}

	@Test
	void artefatoArquivadoNaoRecebeAnexo() throws Exception {
		String init = artifact("Arquivado");
		mvc.perform(delete("/api/v1/artifacts/{a}", init).with(user("ana")).with(csrf())).andExpect(status().is2xxSuccessful());
		upload("ana", init, "a.txt", "text/plain", "x")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_EDITABLE"));
	}

	private ResultActions upload(String username, String artifact, String name, String type, String text)
			throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", name, type, text.getBytes(StandardCharsets.UTF_8));
		return mvc.perform(multipart("/api/v1/artifacts/{a}/attachments", artifact).file(file).with(user(username))
			.with(csrf()));
	}

	private String artifact(String title) throws Exception {
		return JsonPath.read(mvc.perform(post("/api/v1/artifacts").with(user("ana")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"INITIATIVE\",\"title\":\"" + title + "\",\"orgUnitId\":\"" + portfolio + "\"}"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.key");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(post("/api/v1/org-units").with(user("at.admin")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\",\"level\":\"" + level + "\",\"parentId\":\"" + parentId + "\"}"))
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

	private static RequestPostProcessor user(String username) {
		return jwt().jwt(token -> token.subject("sub-" + username).claim("preferred_username", username)
			.claim("name", username));
	}
}
