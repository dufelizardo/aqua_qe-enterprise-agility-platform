package br.com.aquaqe.platform.workitem;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * Artefatos (SSD 3.4) e escopo entre unidades (SSD 2.1), no cenário C2: a Cadeia Pagamentos, sob o Portfólio A,
 * atende também ao Portfólio B.
 *
 * <pre>
 * Empresa ─┬─ Portfólio A ── Cadeia Pagamentos (atende ao Portfólio B) ── Squad Pix
 *          ├─ Portfólio B
 *          └─ Portfólio C ── Cadeia Crédito (sem vínculo)
 * Paula: PRODUCT_MANAGER no Portfólio B · Tiago: TECH_LEAD na Cadeia Pagamentos · Carlos: PRODUCT_MANAGER no Portfólio C
 * </pre>
 */
@SpringBootTest(properties = "aquaqe.access.admin-username=wi.admin")
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkitemIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	String root;
	String portfolioA;
	String portfolioB;
	String portfolioC;
	String cadeiaPagamentos;
	String cadeiaCredito;
	String squadPix;

	@BeforeAll
	void organizacao() throws Exception {
		root = jdbc.queryForObject("select id from organization.org_unit where parent_id is null", UUID.class).toString();
		portfolioA = unit("Portfólio A", "PORTFOLIO", root);
		portfolioB = unit("Portfólio B", "PORTFOLIO", root);
		portfolioC = unit("Portfólio C", "PORTFOLIO", root);
		cadeiaPagamentos = unit("Cadeia Pagamentos", "CADEIA", portfolioA);
		cadeiaCredito = unit("Cadeia Crédito", "CADEIA", portfolioC);
		squadPix = unit("Squad Pix", "SQUAD", cadeiaPagamentos);
		mvc.perform(put("/api/v1/org-units/{id}/links/{target}", cadeiaPagamentos, portfolioB).with(admin()).with(csrf()))
			.andExpect(status().isNoContent());
		grant("paula", "PRODUCT_MANAGER", portfolioB);
		grant("tiago", "TECH_LEAD", cadeiaPagamentos);
		grant("carlos", "PRODUCT_MANAGER", portfolioC);
	}

	@Test
	void configuracaoPadraoDosTiposEWorkflows() throws Exception {
		mvc.perform(get("/api/v1/artifact-types").with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(11))
			.andExpect(jsonPath("$.items[?(@.key=='FEATURE')].parentTypes[*]").value(hasItem("INITIATIVE")))
			.andExpect(jsonPath("$.items[?(@.key=='STORY')].parentRequired").value(hasItem(true)));
		mvc.perform(get("/api/v1/workflows").param("artifactType", "FEATURE").with(user("paula")))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].statuses[0].key").value("BACKLOG"))
			.andExpect(jsonPath("$.items[0].statuses[-1:].key").value(hasItem("CANCELLED")))
			.andExpect(jsonPath("$.items[0].artifactTypes").value(containsInAnyOrder("FEATURE", "SUSTAINING_FEATURE")));
	}

	@Test
	void cadeiaCompartilhadaEntregaFeatureDaIniciativaDeOutroPortfolio() throws Exception {
		String init = create("paula", "{\"type\":\"INITIATIVE\",\"title\":\"Nova experiência de acesso\","
				+ "\"description\":\"Reduzir o abandono no login.\",\"orgUnitId\":\"" + portfolioB + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.key").value(startsWith("INIT-")))
			.andExpect(jsonPath("$.level").value("FL3"))
			.andExpect(jsonPath("$.status.key").value("IDEA"))
			.andExpect(jsonPath("$.orgUnit.level").value("PORTFOLIO"))
			.andReturn().getResponse().getContentAsString();
		String initId = JsonPath.read(init, "$.id");
		String initKey = JsonPath.read(init, "$.key");

		// Regra 5: o Tiago não lê a Iniciativa, mas a Cadeia dele atende ao Portfólio B.
		String feat = create("tiago", "{\"type\":\"FEATURE\",\"title\":\"Autenticação\",\"parentId\":\"" + initId
				+ "\",\"orgUnitId\":\"" + cadeiaPagamentos + "\",\"size\":\"M\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status.key").value("BACKLOG"))
			.andExpect(jsonPath("$.parent.key").value(initKey))
			.andReturn().getResponse().getContentAsString();
		String featKey = JsonPath.read(feat, "$.key");

		// Regra 2: a Paula lê a Feature de outro ramo, mas não edita (403).
		mvc.perform(get("/api/v1/artifacts/{a}", featKey).with(user("paula"))).andExpect(status().isOk());
		mvc.perform(json(patch("/api/v1/artifacts/{a}", featKey), user("paula"), "{\"title\":\"x\",\"version\":0}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		// Regra 3: o Tiago vê o resumo da Iniciativa no contexto, mas não abre a Iniciativa (404).
		mvc.perform(get("/api/v1/artifacts/{a}/context", featKey).with(user("tiago")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ancestors[0].key").value(initKey))
			.andExpect(jsonPath("$.ancestors[0].title").value("Nova experiência de acesso"))
			.andExpect(jsonPath("$.ancestors[0].summary").value("Reduzir o abandono no login."))
			.andExpect(jsonPath("$.ancestors[0].status").value("Ideia"));
		mvc.perform(get("/api/v1/artifacts/{a}", initKey).with(user("tiago")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		// Regra 7: quem não tem relação nenhuma recebe 404 nos dois.
		mvc.perform(get("/api/v1/artifacts/{a}", featKey).with(user("carlos"))).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/artifacts/{a}", initKey).with(user("carlos"))).andExpect(status().isNotFound());

		// Regra 6: a listagem segue o detalhe.
		mvc.perform(get("/api/v1/artifacts").header("X-Workspace-Id", portfolioB).with(user("paula")))
			.andExpect(jsonPath("$.items[*].key").value(hasItem(initKey)))
			.andExpect(jsonPath("$.items[*].key").value(hasItem(featKey)));
		mvc.perform(get("/api/v1/artifacts").header("X-Workspace-Id", root).with(user("carlos")))
			.andExpect(jsonPath("$.items[*].key").value(not(hasItem(featKey))))
			.andExpect(jsonPath("$.items[*].key").value(not(hasItem(initKey))));
		mvc.perform(get("/api/v1/artifacts").header("X-Workspace-Id", portfolioB).param("type", "FEATURE")
				.with(user("paula")))
			.andExpect(jsonPath("$.items[*].key").value(not(hasItem(initKey))));

		// Filhos diretos.
		mvc.perform(get("/api/v1/artifacts/{a}/children", initKey).with(user("paula")))
			.andExpect(jsonPath("$.items[*].key").value(hasItem(featKey)));
	}

	@Test
	void listagemExigeWorkspace() throws Exception {
		mvc.perform(get("/api/v1/artifacts").with(user("paula")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("WORKSPACE_REQUIRED"));
	}

	@Test
	void unidadeDoNivelErradoRecebe422() throws Exception {
		create("wi.admin", "{\"type\":\"INITIATIVE\",\"title\":\"X\",\"orgUnitId\":\"" + cadeiaPagamentos + "\"}")
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("INVALID_ORG_UNIT_TYPE"));
	}

	@Test
	void tudoNasceNaSquad() throws Exception {
		// Bug #66 (2026-11-21): a Squad aceita Iniciativa, Feature e História, com a hierarquia dentro dela.
		String init = JsonPath.read(create("wi.admin",
				"{\"type\":\"INITIATIVE\",\"title\":\"Iniciativa da Squad\",\"orgUnitId\":\"" + squadPix + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String feat = JsonPath.read(create("wi.admin", "{\"type\":\"FEATURE\",\"title\":\"Feature da Squad\",\"parentId\":\""
				+ init + "\",\"orgUnitId\":\"" + squadPix + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		create("wi.admin", "{\"type\":\"STORY\",\"title\":\"História da Squad\",\"parentId\":\"" + feat
				+ "\",\"orgUnitId\":\"" + squadPix + "\"}")
			.andExpect(status().isCreated());
	}

	@Test
	void tipoDoPaiENecessidadeDePai() throws Exception {
		String init = initiative("wi.admin", portfolioA);
		create("wi.admin", "{\"type\":\"STORY\",\"title\":\"X\",\"parentId\":\"" + init + "\",\"orgUnitId\":\"" + squadPix + "\"}")
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("INVALID_PARENT_TYPE"));
		create("wi.admin", "{\"type\":\"FEATURE\",\"title\":\"X\",\"orgUnitId\":\"" + cadeiaPagamentos + "\"}")
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("INVALID_PARENT_TYPE"));
	}

	@Test
	void paiEmOutroRamoSemVinculoRecebe422() throws Exception {
		String init = initiative("wi.admin", portfolioB);
		create("wi.admin", "{\"type\":\"FEATURE\",\"title\":\"X\",\"parentId\":\"" + init + "\",\"orgUnitId\":\""
				+ cadeiaCredito + "\"}")
			.andExpect(status().is(422))
			.andExpect(jsonPath("$.code").value("ORG_UNITS_NOT_LINKED"));
	}

	@Test
	void paiQueOUsuarioNaoLeEFicaSemVinculoParecerInexistente() throws Exception {
		String init = initiative("paula", portfolioB);
		// Carlos (PM no Portfólio C) não lê a Iniciativa e a Cadeia Crédito não atende ao Portfólio B.
		create("carlos", "{\"type\":\"FEATURE\",\"title\":\"X\",\"parentId\":\"" + init + "\",\"orgUnitId\":\""
				+ cadeiaCredito + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("parentId"));
	}

	@Test
	void papelSemPermissaoNoNivelRecebe403() throws Exception {
		// Tech Lead não cria Iniciativa (matriz do SSD 2), nem na própria unidade.
		create("tiago", "{\"type\":\"INITIATIVE\",\"title\":\"X\",\"orgUnitId\":\"" + portfolioA + "\"}")
			.andExpect(status().isForbidden());
	}

	@Test
	void edicaoComVersaoEStatusSoPelasTransicoes() throws Exception {
		String init = initiative("paula", portfolioB);
		// Campo customizado declarado na Iniciativa (#17): só os declarados são aceitos.
		jdbc.update("""
				insert into configuration.custom_field (type_id, key, label, field_type, position)
				select id, 'canal', 'Canal', 'TEXT', 1 from configuration.artifact_type where key = 'INITIATIVE'
				on conflict do nothing
				""");
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"),
				"{\"title\":\"Novo título\",\"size\":\"L\",\"customFields\":{\"canal\":\"app\"},\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Novo título"))
			.andExpect(jsonPath("$.size").value("L"))
			.andExpect(jsonPath("$.customFields.canal").value("app"))
			.andExpect(jsonPath("$.version").value(1));
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"title\":\"Outro\",\"version\":0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"status\":\"DONE\",\"version\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("status"));
		mvc.perform(json(patch("/api/v1/artifacts/{a}", init), user("paula"), "{\"size\":\"XXL\",\"version\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("size"));
	}

	@Test
	void arquivarExigeQueNaoHajaFilhosAtivos() throws Exception {
		String init = initiative("paula", portfolioB);
		String feat = JsonPath.read(create("tiago", "{\"type\":\"FEATURE\",\"title\":\"F\",\"parentId\":\"" + init
				+ "\",\"orgUnitId\":\"" + cadeiaPagamentos + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(delete("/api/v1/artifacts/{a}", init).with(user("paula")).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("HAS_CHILDREN"));
		mvc.perform(delete("/api/v1/artifacts/{a}", feat).with(user("tiago")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(delete("/api/v1/artifacts/{a}", init).with(user("paula")).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/artifacts/{a}", init).with(user("paula")))
			.andExpect(jsonPath("$.archived").value(true));
	}

	@Test
	void chaveLegivelEmMinusculasTambemEncontra() throws Exception {
		String body = create("paula", "{\"type\":\"INITIATIVE\",\"title\":\"Chave\",\"orgUnitId\":\"" + portfolioB + "\"}")
			.andReturn().getResponse().getContentAsString();
		String key = JsonPath.read(body, "$.key");
		mvc.perform(get("/api/v1/artifacts/{a}", key.toLowerCase()).with(user("paula")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.key").value(key));
	}

	@Test
	void mudarOPaiAtualizaOCaminhoDaSubarvore() throws Exception {
		String init1 = initiative("paula", portfolioB);
		String init2 = initiative("paula", portfolioB);
		String feat = JsonPath.read(create("tiago", "{\"type\":\"FEATURE\",\"title\":\"F\",\"parentId\":\"" + init1
				+ "\",\"orgUnitId\":\"" + cadeiaPagamentos + "\"}")
			.andReturn().getResponse().getContentAsString(), "$.id");
		String story = JsonPath.read(create("wi.admin", "{\"type\":\"STORY\",\"title\":\"S\",\"parentId\":\"" + feat
				+ "\",\"orgUnitId\":\"" + squadPix + "\"}")
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(json(patch("/api/v1/artifacts/{a}", feat), user("tiago"), "{\"parentId\":\"" + init2 + "\",\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.parent.id").value(init2));
		String init2Key = JsonPath.read(mvc.perform(get("/api/v1/artifacts/{a}", init2).with(user("paula")))
			.andReturn().getResponse().getContentAsString(), "$.key");
		mvc.perform(get("/api/v1/artifacts/{a}/context", story).with(user("tiago")))
			.andExpect(jsonPath("$.ancestors[0].key").value(init2Key));
	}

	// ------------------------------------------------------------------------------------------------ apoio

	private String initiative(String username, String unitId) throws Exception {
		String body = create(username, "{\"type\":\"INITIATIVE\",\"title\":\"Iniciativa\",\"orgUnitId\":\"" + unitId + "\"}")
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private ResultActions create(String username, String body) throws Exception {
		return mvc.perform(json(post("/api/v1/artifacts"), user(username), body));
	}

	private String unit(String name, String level, String parentId) throws Exception {
		String body = mvc.perform(json(post("/api/v1/org-units"), admin(),
				"{\"name\":\"" + name + "\",\"level\":\"" + level + "\",\"parentId\":\"" + parentId + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	/** Cria a conta (primeiro acesso) e atribui o papel direto no banco: os endpoints de papéis entram depois. */
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

	private static RequestPostProcessor admin() {
		return user("wi.admin");
	}

	private static RequestPostProcessor user(String username) {
		return jwt().jwt(token -> token.subject("sub-" + username).claim("preferred_username", username)
			.claim("name", username));
	}
}
