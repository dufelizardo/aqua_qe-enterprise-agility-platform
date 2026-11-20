package br.com.aquaqe.platform.identity;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Autenticação da API (ADR-08, ADR-16 e ADR-17).
 *
 * @param publicUrl   endereço do Keycloak que o navegador usa (ex.: http://aquaqe-auth.local); define o emissor
 * @param internalUrl endereço do Keycloak que a API usa dentro da rede dos containers
 * @param realm       realm do ambiente (aquaqe-dev ou aquaqe-prod)
 * @param clientId    cliente OIDC da aplicação web (fluxo BFF)
 * @param mode        {@code oidc} (login real) ou {@code dev-persona} (sem senha, só fora de produção)
 * @param devPersonas personas disponíveis no modo {@code dev-persona}; a primeira é a padrão
 */
@ConfigurationProperties("aquaqe.auth")
public record AuthProperties(
	String publicUrl,
	String internalUrl,
	String realm,
	String clientId,
	@DefaultValue("oidc") AuthMode mode,
	@DefaultValue List<DevPersona> devPersonas) {

	String realmPublicUrl() {
		return publicUrl + "/realms/" + realm;
	}

	public enum AuthMode {
		/** Login pelo Keycloak (BFF no navegador, token Bearer nas integrações). */
		OIDC,
		/** Sem senha: a API age como uma persona de teste. Proibido em produção. */
		DEV_PERSONA
	}

	/** Persona de teste do modo {@code dev-persona}; os papéis seguem o SSD, seção 2. */
	public record DevPersona(String username, String name, String email, String role) {
	}
}
