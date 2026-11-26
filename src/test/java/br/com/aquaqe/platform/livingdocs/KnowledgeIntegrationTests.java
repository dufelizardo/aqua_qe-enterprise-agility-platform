package br.com.aquaqe.platform.livingdocs;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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

/** Páginas de conhecimento por unidade (Proposta 07, Fase A; SSD 3.7.1 e 2.1, regra 9). */
@SpringBootTest(properties = "aquaqe.access.admin-username=kn.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KnowledgeIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String portfolio;
	String squad;
	String outroPortfolio;

	@BeforeAll
	void organizacao() throws Exception {
		String root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolio = unit("Portfólio KN", "PORTFOLIO", root);
		String cadeia = unit("Cadeia KN", "CADEIA", portfolio);
		squad = unit("Squad KN", "SQUAD", cadeia);
		outroPortfolio = unit("Outro Portfólio KN", "PORTFOLIO", root);
		grant("pedro", "PRODUCT_MANAGER", portfolio);
		grant("bia", "BUSINESS_OWNER", portfolio);
		grant("vera", "VIEWER", portfolio);
		grant("sara", "SQUAD_MEMBER", squad);
		grant("otto", "PRODUCT_MANAGER", outroPortfolio);
	}

	@Test
	void paginaLivrePublicaAoSalvarComSubpaginaEHistorico() throws Exception {
		String page = id(create("pedro", portfolio, "{\"title\":\"Estratégia\"}")
			.andExpect(jsonPath("$.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.sections[*].key").value(contains("content"))));
		String sub = id(create("pedro", portfolio, "{\"title\":\"Objetivos\",\"parentId\":\"" + page + "\"}")
			.andExpect(jsonPath("$.parentId").value(page)));

		save("pedro", page, 1, body("content", "Crescer em **canais digitais**")).andExpect(status().isOk())
			.andExpect(jsonPath("$.versionNumber").value(2))
			.andExpect(jsonPath("$.publishedVersion").value(2))
			.andExpect(jsonPath("$.sections[0].blocks[0].text").value("Crescer em **canais digitais**"));
		save("pedro", page, 1, body("content", "Edição concorrente")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
		mvc.perform(get("/api/v1/pages/{id}/versions", page).with(user("pedro")))
			.andExpect(jsonPath("$.items[*].number").value(contains(2, 1)));
		mvc.perform(get("/api/v1/pages/{id}/versions/{n}", page, 1).with(user("pedro")))
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.sections[0].blocks", hasSize(0)));

		// Regra 9: o membro da Squad abaixo lê o que está publicado, sem editar nem ver o histórico.
		mvc.perform(get("/api/v1/org-units/{u}/pages", portfolio).with(user("sara")))
			.andExpect(jsonPath("$.access").value("PUBLISHED"))
			.andExpect(jsonPath("$.canEdit").value(false))
			.andExpect(jsonPath("$.items[*].id").value(hasItem(sub)));
		mvc.perform(get("/api/v1/pages/{id}", page).with(user("sara")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.access").value("PUBLISHED"))
			.andExpect(jsonPath("$.versionNumber").value(2));
		mvc.perform(get("/api/v1/pages/{id}/versions", page).with(user("sara"))).andExpect(status().isForbidden());
		save("sara", page, 2, body("content", "Não pode")).andExpect(status().isForbidden());
		// Outro ramo: não sabe que a página existe.
		mvc.perform(get("/api/v1/pages/{id}", page).with(user("otto"))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/org-units/{u}/pages", portfolio).with(user("otto"))).andExpect(status().isNotFound());
	}

	@Test
	void regimentoPassaPorRevisaoAprovacaoEPublicacaoSeparadas() throws Exception {
		// O regimento é independente do Wiki: um por unidade, por endpoint próprio.
		mvc.perform(get("/api/v1/org-units/{u}/regimento", portfolio).with(user("pedro")))
			.andExpect(jsonPath("$.regimento").doesNotExist())
			.andExpect(jsonPath("$.canEdit").value(true));
		String reg = id(mvc.perform(json(post("/api/v1/org-units/{u}/regimento", portfolio), user("pedro"),
				"{\"title\":\"Regimento do Portfólio\"}"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.governed").value(true))
			.andExpect(jsonPath("$.sections", hasSize(16))));
		mvc.perform(get("/api/v1/pages/{id}", reg).with(user("sara"))).andExpect(status().isNotFound());
		mvc.perform(json(post("/api/v1/org-units/{u}/regimento", portfolio), user("pedro"), "{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REGIMENTO_EXISTS"));
		mvc.perform(get("/api/v1/org-units/{u}/pages", portfolio).with(user("pedro")))
			.andExpect(jsonPath("$.items[*].id").value(not(hasItem(reg))));
		create("pedro", portfolio, "{\"title\":\"X\",\"type\":\"REGIMENTO\"}").andExpect(status().isBadRequest());

		changeStatus("pedro", reg, "APPROVED").andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("TRANSITION_NOT_ALLOWED"));
		changeStatus("pedro", reg, "IN_REVIEW").andExpect(status().isOk());
		save("pedro", reg, 1, regimento(reg, "x")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_EDITABLE"));
		changeStatus("pedro", reg, "APPROVED").andExpect(status().isForbidden());
		changeStatus("bia", reg, "APPROVED").andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("DOCUMENT_INCOMPLETE"))
			.andExpect(jsonPath("$.missingSections", hasSize(5)));
		changeStatus("bia", reg, "DRAFT").andExpect(status().isOk());

		save("pedro", reg, 1, regimento(reg, "Texto")).andExpect(status().isOk())
			.andExpect(jsonPath("$.missingSections", hasSize(0)));
		changeStatus("pedro", reg, "IN_REVIEW").andExpect(status().isOk());
		changeStatus("bia", reg, "APPROVED").andExpect(status().isOk())
			.andExpect(jsonPath("$.approvedVersion").value(2))
			.andExpect(jsonPath("$.publishedVersion").doesNotExist());
		mvc.perform(get("/api/v1/pages/{id}", reg).with(user("sara"))).andExpect(status().isNotFound());
		changeStatus("bia", reg, "PUBLISHED").andExpect(status().isOk())
			.andExpect(jsonPath("$.publishedVersion").value(2));
		mvc.perform(get("/api/v1/pages/{id}", reg).with(user("sara"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.versionNumber").value(2));
		mvc.perform(get("/api/v1/org-units/{u}/regimento", portfolio).with(user("sara")))
			.andExpect(jsonPath("$.access").value("PUBLISHED"))
			.andExpect(jsonPath("$.regimento.id").value(reg));

		// Nova edição volta a rascunho; quem está embaixo continua vendo a versão publicada.
		save("pedro", reg, 2, regimento(reg, "Texto revisto")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.publishedVersion").value(2));
		mvc.perform(get("/api/v1/pages/{id}", reg).with(user("sara")))
			.andExpect(jsonPath("$.versionNumber").value(2))
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
	}

	@Test
	void leitorNaoEditaEMoverNaoCriaCiclo() throws Exception {
		create("vera", portfolio, "{\"title\":\"Não pode\"}").andExpect(status().isForbidden());
		String a = id(create("pedro", portfolio, "{\"title\":\"A\"}"));
		String b = id(create("pedro", portfolio, "{\"title\":\"B\",\"parentId\":\"" + a + "\"}"));

		mvc.perform(json(patch("/api/v1/pages/{id}", a), user("pedro"), "{\"parentId\":\"" + b + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("parentId"));
		mvc.perform(json(post("/api/v1/pages/{id}/archive", a), user("pedro"), "{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HAS_CHILDREN"));
		mvc.perform(json(patch("/api/v1/pages/{id}", b), user("pedro"), "{\"parentId\":null,\"title\":\"B raiz\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").doesNotExist())
			.andExpect(jsonPath("$.title").value("B raiz"));
		mvc.perform(json(post("/api/v1/pages/{id}/archive", a), user("pedro"), "{\"reason\":\"Obsoleta\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archived").value(true));
		mvc.perform(get("/api/v1/org-units/{u}/pages", portfolio).with(user("pedro")))
			.andExpect(jsonPath("$.items[*].id").value(not(hasItem(a))));
		mvc.perform(get("/api/v1/org-units/{u}/pages", portfolio).param("archived", "true").with(user("pedro")))
			.andExpect(jsonPath("$.items[*].id").value(contains(a)));
		save("pedro", a, 1, body("content", "x")).andExpect(status().isConflict());
		mvc.perform(post("/api/v1/pages/{id}/restore", a).with(user("pedro")).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archived").value(false));
	}

	@Test
	void secoesSeguemOTemplateEBlocosConhecidos() throws Exception {
		String page = id(create("pedro", portfolio, "{\"title\":\"Validação\"}"));
		save("pedro", page, 1, "{\"sections\":[{\"key\":\"outra\",\"blocks\":[]}]}").andExpect(status().isBadRequest());
		save("pedro", page, 1, "{\"sections\":[{\"key\":\"content\",\"blocks\":[{\"type\":\"script\"}]}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("sections.content"));
	}

	@Test
	void iconeTarefasECoresDoEditor() throws Exception {
		// #86: ícone da página (emoji), lista de tarefas e cores (só #rrggbb).
		String page = id(create("pedro", portfolio, "{\"title\":\"Editor 86\"}"));
		mvc.perform(json(patch("/api/v1/pages/{id}", page), user("pedro"), "{\"icon\":\"🚀\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.icon").value("🚀"))
			.andExpect(jsonPath("$.title").value("Editor 86"))
			.andExpect(jsonPath("$.versionNumber").value(1));
		mvc.perform(get("/api/v1/org-units/{u}/pages", portfolio).with(user("pedro")))
			.andExpect(jsonPath("$.items[?(@.id == '" + page + "')].icon").value(contains("🚀")));
		mvc.perform(json(patch("/api/v1/pages/{id}", page), user("pedro"), "{\"icon\":\"<b>\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("icon"));
		mvc.perform(json(patch("/api/v1/pages/{id}", page), user("pedro"), "{\"icon\":null}"))
			.andExpect(jsonPath("$.icon").value(org.hamcrest.Matchers.nullValue()));

		String doc = "{\"type\":\"doc\",\"content\":[{\"type\":\"taskList\",\"content\":[{\"type\":\"taskItem\","
				+ "\"attrs\":{\"checked\":true},\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\","
				+ "\"text\":\"Feito\",\"marks\":[{\"type\":\"textStyle\",\"attrs\":{\"color\":\"#b42318\"}},"
				+ "{\"type\":\"highlight\",\"attrs\":{\"color\":\"#fef08a\"}}]}]}]}]}]}";
		save("pedro", page, 1, "{\"sections\":[{\"key\":\"content\",\"blocks\":[{\"type\":\"richtext\",\"doc\":" + doc + "}]}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sections[0].blocks[0].doc.content[0].type").value("taskList"));
		String evil = doc.replace("#b42318", "red; background: url(x)");
		save("pedro", page, 2, "{\"sections\":[{\"key\":\"content\",\"blocks\":[{\"type\":\"richtext\",\"doc\":" + evil + "}]}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("sections.content"));
	}

	@Test
	void editorContinuoGravaRichtext() throws Exception {
		// Bug #67: o editor contínuo (Tiptap/ProseMirror) grava a seção como um bloco richtext com o documento.
		String page = id(create("pedro", portfolio, "{\"title\":\"Editor contínuo\"}"));
		String doc = "{\"type\":\"doc\",\"content\":[{\"type\":\"heading\",\"attrs\":{\"level\":2},"
				+ "\"content\":[{\"type\":\"text\",\"text\":\"Objetivos\"}]},{\"type\":\"paragraph\",\"content\":["
				+ "{\"type\":\"text\",\"text\":\"Crescer\",\"marks\":[{\"type\":\"bold\"},{\"type\":\"underline\"}]}]}]}";
		save("pedro", page, 1, "{\"sections\":[{\"key\":\"content\",\"blocks\":[{\"type\":\"richtext\",\"doc\":" + doc + "}]}]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sections[0].blocks[0].type").value("richtext"))
			.andExpect(jsonPath("$.sections[0].blocks[0].doc.content[1].content[0].marks[1].type").value("underline"));
		save("pedro", page, 2, "{\"sections\":[{\"key\":\"content\",\"blocks\":[{\"type\":\"richtext\"}]}]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("sections.content"));

		// No regimento, a seção obrigatória preenchida pelo editor contínuo conta para a aprovação.
		String outra = unit("Portfólio Editor", "PORTFOLIO",
			jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString());
		grant("bia", "BUSINESS_OWNER", outra);
		String reg = id(mvc.perform(json(post("/api/v1/org-units/{u}/regimento", outra), user("kn.admin"), "{}")));
		List<String> keys = JsonPath.read(mvc.perform(get("/api/v1/pages/{id}", reg).with(user("kn.admin")))
			.andReturn().getResponse().getContentAsString(), "$.sections[*].key");
		String sections = keys.stream()
			.map(k -> "{\"key\":\"" + k + "\",\"blocks\":[{\"type\":\"richtext\",\"doc\":" + doc + "}]}")
			.collect(Collectors.joining(",", "{\"sections\":[", "]}"));
		save("kn.admin", reg, 1, sections).andExpect(status().isOk()).andExpect(jsonPath("$.missingSections", hasSize(0)));
		changeStatus("kn.admin", reg, "IN_REVIEW").andExpect(status().isOk());
		changeStatus("bia", reg, "APPROVED").andExpect(status().isOk());
	}

	@Test
	void reorganizarAWikiPreservaPaginaEHistorico() throws Exception {
		// PROP-002: reordenar, mover com subpáginas e promover, sem versão nova nem mudança de id.
		String unidade = unit("Portfólio Organiza", "PORTFOLIO",
			jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString());
		grant("pedro", "PRODUCT_MANAGER", unidade);
		String a = id(create("pedro", unidade, "{\"title\":\"A\"}"));
		String b = id(create("pedro", unidade, "{\"title\":\"B\"}"));
		String c = id(create("pedro", unidade, "{\"title\":\"C\"}"));
		String d = id(create("pedro", unidade, "{\"title\":\"D\",\"parentId\":\"" + a + "\"}"));

		rank(c, "{\"beforeId\":\"" + a + "\"}").andExpect(status().isOk());
		rootOrder(unidade).andExpect(jsonPath("$.items[?(@.parentId == null)].id").value(contains(c, a, b)));
		rank(a, "{\"afterId\":\"" + b + "\"}").andExpect(status().isOk());
		rootOrder(unidade).andExpect(jsonPath("$.items[?(@.parentId == null)].id").value(contains(c, b, a)));

		// Mover A para dentro de B leva D junto; não cria versão.
		rank(a, "{\"parentId\":\"" + b + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").value(b))
			.andExpect(jsonPath("$.versionNumber").value(1));
		mvc.perform(get("/api/v1/pages/{id}", d).with(user("pedro"))).andExpect(jsonPath("$.parentId").value(a));
		// Promover A de volta para a raiz, logo depois de B.
		rank(a, "{\"parentId\":null,\"afterId\":\"" + b + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.parentId").doesNotExist());
		rootOrder(unidade).andExpect(jsonPath("$.items[?(@.parentId == null)].id").value(contains(c, b, a)));

		rank(a, "{\"parentId\":\"" + d + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("parentId"));
		rank(c, "{\"beforeId\":\"" + d + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("beforeId"));
		rank(c, "{}").andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/v1/pages/{id}/rank", c), user("vera"), "{\"beforeId\":\"" + b + "\"}"))
			.andExpect(status().isNotFound());
		Integer moved = jdbc.queryForObject(
			"select count(*) from events.outbox_event where type = 'knowledge_page.moved' and envelope -> 'aggregate' ->> 'id' = ?",
			Integer.class, a);
		org.junit.jupiter.api.Assertions.assertEquals(3, moved);
	}

	private ResultActions rank(String page, String body) throws Exception {
		return mvc.perform(json(post("/api/v1/pages/{id}/rank", page), user("pedro"), body));
	}

	private ResultActions rootOrder(String unit) throws Exception {
		return mvc.perform(get("/api/v1/org-units/{u}/pages", unit).with(user("pedro")));
	}

	@Test
	void operacoesVaoParaAOutboxDaAuditoria() throws Exception {
		String page = id(create("pedro", portfolio, "{\"title\":\"Auditada\"}"));
		save("pedro", page, 1, body("content", "Texto")).andExpect(status().isOk());
		List<String> types = jdbc.queryForList(
			"select type from events.outbox_event where envelope -> 'aggregate' ->> 'id' = ?", String.class, page);
		org.hamcrest.MatcherAssert.assertThat(types,
			containsInAnyOrder("knowledge_page.created", "knowledge_page.updated"));
	}

	@Test
	void fazerUmaCopiaFicaLogoDepoisComOConteudoAtualSemSubpaginas() throws Exception {
		String unit = unit("Portfólio Cópia KN", "PORTFOLIO",
			jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString());
		grant("pedro", "PRODUCT_MANAGER", unit);
		grant("vera", "VIEWER", unit);
		String first = id(create("pedro", unit, "{\"title\":\"Primeira\"}"));
		String original = id(create("pedro", unit, "{\"title\":\"Original\"}"));
		String last = id(create("pedro", unit, "{\"title\":\"Última\"}"));
		create("pedro", unit, "{\"title\":\"Filha\",\"parentId\":\"" + original + "\"}").andExpect(status().isCreated());
		save("pedro", original, 1, body("content", "Texto atual")).andExpect(status().isOk());

		String copy = id(mvc.perform(json(post("/api/v1/pages/{id}/copy", original), user("pedro"), "{}"))
			.andExpect(jsonPath("$.title").value("Cópia de Original"))
			.andExpect(jsonPath("$.parentId").doesNotExist())
			.andExpect(jsonPath("$.versionNumber").value(1))
			.andExpect(jsonPath("$.sections[0].blocks[0].text").value("Texto atual")));
		mvc.perform(get("/api/v1/org-units/{u}/pages", unit).with(user("pedro")))
			.andExpect(jsonPath("$.items[?(@.parentId == null)].id").value(contains(first, original, copy, last)))
			.andExpect(jsonPath("$.items[?(@.parentId == '" + copy + "')]").isEmpty());
		mvc.perform(json(post("/api/v1/pages/{id}/copy", original), user("pedro"), "{\"title\":\"Outro nome\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Outro nome"));

		mvc.perform(json(post("/api/v1/pages/{id}/copy", original), user("vera"), "{}")).andExpect(status().isForbidden());
		String reg = id(mvc.perform(json(post("/api/v1/org-units/{u}/regimento", unit), user("pedro"),
			"{\"title\":\"Regimento da cópia\"}")));
		mvc.perform(json(post("/api/v1/pages/{id}/copy", reg), user("pedro"), "{}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("NOT_COPYABLE"));
		String payload = jdbc.queryForObject(
			"select envelope -> 'payload' ->> 'copiedFrom' from events.outbox_event where envelope -> 'aggregate' ->> 'id' = ?",
			String.class, copy);
		org.junit.jupiter.api.Assertions.assertEquals(original, payload);
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private ResultActions create(String username, String unit, String body) throws Exception {
		return mvc.perform(json(post("/api/v1/org-units/{u}/pages", unit), user(username), body));
	}

	private ResultActions save(String username, String page, int base, String sectionsBody) throws Exception {
		String body = "{\"baseVersion\":" + base + "," + sectionsBody.substring(1);
		return mvc.perform(json(put("/api/v1/pages/{id}", page), user(username), body));
	}

	private ResultActions changeStatus(String username, String page, String to) throws Exception {
		return mvc.perform(json(post("/api/v1/pages/{id}/status", page), user(username),
			"{\"status\":\"" + to + "\",\"comment\":\"ok\"}"));
	}

	private static String body(String key, String text) {
		return "{\"sections\":[{\"key\":\"" + key + "\",\"blocks\":[{\"type\":\"paragraph\",\"text\":\"" + text + "\"}]}]}";
	}

	/** Todas as seções do regimento preenchidas com o texto. */
	private String regimento(String page, String text) throws Exception {
		List<String> keys = JsonPath.read(mvc.perform(get("/api/v1/pages/{id}", page).with(user("pedro")))
			.andReturn().getResponse().getContentAsString(), "$.sections[*].key");
		return keys.stream()
			.map(k -> "{\"key\":\"" + k + "\",\"blocks\":[{\"type\":\"paragraph\",\"text\":\"" + text + "\"}]}")
			.collect(Collectors.joining(",", "{\"sections\":[", "]}"));
	}

	private static String id(ResultActions result) throws Exception {
		return JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String unit(String name, String level, String parentId) throws Exception {
		return JsonPath.read(mvc.perform(json(post("/api/v1/org-units"), user("kn.admin"),
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
