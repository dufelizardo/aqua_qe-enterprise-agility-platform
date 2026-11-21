package br.com.aquaqe.platform.organization;

import static org.hamcrest.Matchers.hasItem;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/** Estrutura organizacional (SSD 3.2) e a permissão "gerir configuração" (SSD 2 e 2.1, regra 1). */
@SpringBootTest(properties = "aquaqe.access.admin-username=org.admin")
@AutoConfigureMockMvc
@Testcontainers
class OrganizationIntegrationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	OrgUnits orgUnits;

	@Test
	void niveisDaInstalacaoPadrao() throws Exception {
		mvc.perform(get("/api/v1/org-levels").with(user("leitor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(5))
			.andExpect(jsonPath("$.items[0].key").value("ENTERPRISE"))
			.andExpect(jsonPath("$.items[0].flightLevel").doesNotExist())
			.andExpect(jsonPath("$.items[2].key").value("PORTFOLIO"))
			.andExpect(jsonPath("$.items[2].flightLevel").value("FL3"))
			.andExpect(jsonPath("$.items[4].flightLevel").value("FL1"));
	}

	@Test
	void raizUnicaCriadaNaInstalacao() throws Exception {
		mvc.perform(get("/api/v1/org-units").param("level", "ENTERPRISE").with(user("leitor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(orgUnits.rootId().toString()))
			.andExpect(jsonPath("$.items[0].parentId").doesNotExist());
	}

	@Test
	void adminCriaUnidadesEODetalheMostraOCaminhoAteARaiz() throws Exception {
		String portfolio = createUnit("Portfólio Canais", "PORTFOLIO", orgUnits.rootId());
		String cadeia = createUnit("Cadeia Pagamentos", "CADEIA", UUID.fromString(portfolio));

		mvc.perform(get("/api/v1/org-units/{id}", cadeia).with(user("leitor")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Cadeia Pagamentos"))
			.andExpect(jsonPath("$.level.flightLevel").value("FL2"))
			.andExpect(jsonPath("$.parentId").value(portfolio))
			.andExpect(jsonPath("$.path.length()").value(2))
			.andExpect(jsonPath("$.path[0].id").value(orgUnits.rootId().toString()))
			.andExpect(jsonPath("$.path[1].name").value("Portfólio Canais"));
	}

	@Test
	void arvorePodePularNiveis() throws Exception {
		// Cenário C3 (SSD 2.1): sem Unidade de Negócio, o Portfólio fica direto sob a Empresa.
		createUnit("Portfólio Sem UN", "PORTFOLIO", orgUnits.rootId());
	}

	@Test
	void nivelDoPaiPrecisaSerAnterior() throws Exception {
		String squad = createUnit("Squad Pix", "SQUAD", orgUnits.rootId());
		mvc.perform(json(post("/api/v1/org-units"), admin(),
				"{\"name\":\"Cadeia Errada\",\"level\":\"CADEIA\",\"parentId\":\"" + squad + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("level"));
	}

	@Test
	void dadosInvalidosNaCriacao() throws Exception {
		mvc.perform(json(post("/api/v1/org-units"), admin(),
				"{\"name\":\"  \",\"level\":\"PORTFOLIO\",\"parentId\":\"" + orgUnits.rootId() + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		mvc.perform(json(post("/api/v1/org-units"), admin(),
				"{\"name\":\"X\",\"level\":\"INEXISTENTE\",\"parentId\":\"" + orgUnits.rootId() + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("level"));
		mvc.perform(json(post("/api/v1/org-units"), admin(), "{\"name\":\"X\",\"level\":\"PORTFOLIO\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("parentId"));
	}

	@Test
	void quemNaoGereConfiguracaoRecebe403() throws Exception {
		mvc.perform(json(post("/api/v1/org-units"), user("sem.papel"),
				"{\"name\":\"Portfólio\",\"level\":\"PORTFOLIO\",\"parentId\":\"" + orgUnits.rootId() + "\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void unidadeInexistenteRecebe404() throws Exception {
		mvc.perform(get("/api/v1/org-units/{id}", UUID.randomUUID()).with(user("leitor")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void edicaoUsaVersaoParaConcorrencia() throws Exception {
		String unit = createUnit("Portfólio Antigo", "PORTFOLIO", orgUnits.rootId());

		mvc.perform(json(patch("/api/v1/org-units/{id}", unit), admin(), "{\"name\":\"Portfólio Novo\",\"version\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Portfólio Novo"))
			.andExpect(jsonPath("$.version").value(1));
		mvc.perform(json(patch("/api/v1/org-units/{id}", unit), admin(), "{\"active\":false,\"version\":0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
	}

	@Test
	void raizNaoPodeSerDesativada() throws Exception {
		mvc.perform(json(patch("/api/v1/org-units/{id}", orgUnits.rootId()), admin(), "{\"active\":false,\"version\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("active"));
	}

	@Test
	void cadeiaCompartilhadaAtendeOutroPortfolio() throws Exception {
		// Cenário C2 (SSD 2.1): a Cadeia Pagamentos, sob o Portfólio A, atende também ao Portfólio B.
		String portfolioA = createUnit("Portfólio A", "PORTFOLIO", orgUnits.rootId());
		String portfolioB = createUnit("Portfólio B", "PORTFOLIO", orgUnits.rootId());
		String cadeia = createUnit("Cadeia Compartilhada", "CADEIA", UUID.fromString(portfolioA));

		mvc.perform(put("/api/v1/org-units/{id}/links/{target}", cadeia, portfolioB).with(admin()).with(csrf()))
			.andExpect(status().isNoContent());

		mvc.perform(get("/api/v1/org-units/{id}/links", cadeia).with(user("leitor")))
			.andExpect(jsonPath("$.serves[*].id", hasItem(portfolioB)));
		mvc.perform(get("/api/v1/org-units/{id}", portfolioB).with(user("leitor")))
			.andExpect(jsonPath("$.links.servedBy[*].id", hasItem(cadeia)));

		mvc.perform(delete("/api/v1/org-units/{id}/links/{target}", cadeia, portfolioB).with(admin()).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/org-units/{id}/links", cadeia).with(user("leitor")))
			.andExpect(jsonPath("$.serves.length()").value(0));
	}

	@Test
	void vinculoNaMesmaLinhaDaArvoreEhRecusado() throws Exception {
		String portfolio = createUnit("Portfólio Linha", "PORTFOLIO", orgUnits.rootId());
		String cadeia = createUnit("Cadeia Linha", "CADEIA", UUID.fromString(portfolio));

		mvc.perform(put("/api/v1/org-units/{id}/links/{target}", cadeia, portfolio).with(admin()).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void quemNaoGereConfiguracaoNaoCriaVinculo() throws Exception {
		String a = createUnit("Portfólio V1", "PORTFOLIO", orgUnits.rootId());
		String b = createUnit("Portfólio V2", "PORTFOLIO", orgUnits.rootId());
		mvc.perform(put("/api/v1/org-units/{id}/links/{target}", a, b).with(user("sem.papel")).with(csrf()))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminIndicadoNaInstalacaoRecebePapelNaRaiz() throws Exception {
		mvc.perform(get("/api/v1/me").with(admin()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignments[0].role").value("PLATFORM_ADMIN"))
			.andExpect(jsonPath("$.assignments[0].orgUnit.id").value(orgUnits.rootId().toString()))
			.andExpect(jsonPath("$.assignments[0].orgUnit.level").value("ENTERPRISE"));
	}

	@Test
	void listagemPaginadaRespeitaOLimite() throws Exception {
		mvc.perform(get("/api/v1/org-units").param("size", "101").with(user("leitor")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("size"));
	}

	@Test
	void cardinalidadeDoisPortfoliosDuasCadeiasDuasSquads() throws Exception {
		// PROPOSAL-07, seção 11: várias unidades em cada nível, caminhos corretos e "atende a" sem duplicar hierarquia.
		java.util.Map<String, String> ids = new java.util.LinkedHashMap<>();
		for (int p = 1; p <= 2; p++) {
			String portfolio = createUnit("Card P" + p, "PORTFOLIO", orgUnits.rootId());
			ids.put("P" + p, portfolio);
			for (int c = 1; c <= 2; c++) {
				String cadeia = createUnit("Card C" + p + c, "CADEIA", UUID.fromString(portfolio));
				ids.put("C" + p + c, cadeia);
				for (int q = 1; q <= 2; q++) {
					ids.put("S" + p + c + q, createUnit("Card S" + p + c + q, "SQUAD", UUID.fromString(cadeia)));
				}
			}
		}
		for (int p = 1; p <= 2; p++) {
			mvc.perform(get("/api/v1/org-units").param("parentId", ids.get("P" + p)).with(user("leitor")))
				.andExpect(jsonPath("$.totalItems").value(2));
			for (int c = 1; c <= 2; c++) {
				mvc.perform(get("/api/v1/org-units").param("parentId", ids.get("C" + p + c)).with(user("leitor")))
					.andExpect(jsonPath("$.totalItems").value(2));
			}
		}
		mvc.perform(get("/api/v1/org-units/{id}", ids.get("S212")).with(user("leitor")))
			.andExpect(jsonPath("$.path[*].id").value(org.hamcrest.Matchers.contains(orgUnits.rootId().toString(),
				ids.get("P2"), ids.get("C21"))));

		// A Cadeia C11 passa a atender o Portfólio P2: continua filha só de P1.
		mvc.perform(put("/api/v1/org-units/{id}/links/{target}", ids.get("C11"), ids.get("P2")).with(admin()).with(csrf()))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/org-units").param("parentId", ids.get("P2")).with(user("leitor")))
			.andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(hasItem(ids.get("C11")))));
		mvc.perform(get("/api/v1/org-units/{id}", ids.get("C11")).with(user("leitor")))
			.andExpect(jsonPath("$.parentId").value(ids.get("P1")))
			.andExpect(jsonPath("$.links.serves[*].id").value(hasItem(ids.get("P2"))));
	}

	private String createUnit(String name, String level, UUID parentId) throws Exception {
		String body = mvc.perform(json(post("/api/v1/org-units"), admin(),
				"{\"name\":\"" + name + "\",\"level\":\"" + level + "\",\"parentId\":\"" + parentId + "\"}"))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, RequestPostProcessor user,
			String body) {
		return request.with(user).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static RequestPostProcessor admin() {
		return user("org.admin");
	}

	private static RequestPostProcessor user(String username) {
		return jwt().jwt(token -> token.subject("sub-" + username).claim("preferred_username", username)
			.claim("name", username));
	}
}
