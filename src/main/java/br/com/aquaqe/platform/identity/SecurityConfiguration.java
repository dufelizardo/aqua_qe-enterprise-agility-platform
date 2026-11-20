package br.com.aquaqe.platform.identity;

import java.util.function.BiFunction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Segurança da API (ADR-08 e ADR-16).
 * <ul>
 * <li>Navegador: login OIDC conduzido pelo backend (BFF) e sessão por cookie; o SPA nunca vê tokens.</li>
 * <li>Integrações e testes: token Bearer emitido pelo mesmo Keycloak (resource server).</li>
 * <li>{@code /api/**} sem autenticação responde 401, sem redirecionar; o SPA decide ir para o login.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

	private static final String[] PUBLIC_PATHS = { "/actuator/health", "/actuator/health/**", "/actuator/info" };

	/**
	 * Modo sem senha do ambiente dev (ADR-17): toda requisição age como a persona escolhida. Sem Keycloak e sem logout;
	 * a troca de persona é feita por {@code PUT /api/v1/dev/persona}. A {@link AuthModeSafetyCheck} impede este modo em
	 * produção.
	 */
	@Bean
	@ConditionalOnProperty(name = "aquaqe.auth.mode", havingValue = "dev-persona")
	SecurityFilterChain devPersonaSecurityFilterChain(HttpSecurity http, AuthProperties auth) throws Exception {
		http
			.addFilterBefore(new DevPersonaFilter(auth), AnonymousAuthenticationFilter.class)
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.spa())
			.logout(logout -> logout.logoutSuccessHandler(redirectTo((request, authentication) -> "/")));
		return http.build();
	}

	@Bean
	@ConditionalOnProperty(name = "aquaqe.auth.mode", havingValue = "oidc", matchIfMissing = true)
	SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties auth) throws Exception {
		http
			.authorizeHttpRequests(requests -> requests
				.requestMatchers(PUBLIC_PATHS).permitAll()
				.anyRequest().authenticated())
			// Depois do login, o usuário vai para Meu trabalho; a raiz é a landing pública do SPA.
			.oauth2Login(login -> login.defaultSuccessUrl("/home", true))
			.oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
			.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
				new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
				PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
			.csrf(csrf -> csrf
				.spa()
				.ignoringRequestMatchers(request -> request.getHeader("Authorization") != null))
			.logout(logout -> logout.logoutSuccessHandler(keycloakLogout(auth)));
		return http.build();
	}

	/**
	 * Encerra também a sessão no Keycloak e volta para a página inicial da aplicação. O SPA faz o logout por
	 * {@code POST} via XHR, que não pode seguir um redirect para outro domínio; para ele a resposta é JSON com o
	 * endereço a abrir. Um formulário comum recebe o redirect.
	 */
	private static LogoutSuccessHandler keycloakLogout(AuthProperties auth) {
		// Usa a autenticação entregue ao handler: quando ele roda, o SecurityContext já foi limpo. Sem o id_token_hint,
		// o Keycloak pede confirmação em vez de voltar para a aplicação.
		return redirectTo((request, authentication) -> {
			String home = ServletUriComponentsBuilder.fromContextPath(request).path("/").toUriString();
			UriComponentsBuilder logout = UriComponentsBuilder
				.fromUriString(auth.realmPublicUrl() + "/protocol/openid-connect/logout")
				.queryParam("client_id", auth.clientId())
				.queryParam("post_logout_redirect_uri", home);
			if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
				logout.queryParam("id_token_hint", user.getIdToken().getTokenValue());
			}
			return logout.encode().toUriString();
		});
	}

	/** Redireciona para o endereço calculado; para o SPA (XHR com Accept JSON), devolve o endereço em JSON. */
	private static LogoutSuccessHandler redirectTo(BiFunction<HttpServletRequest, Authentication, String> targetFor) {
		return (request, response, authentication) -> {
			String target = targetFor.apply(request, authentication);
			String accept = request.getHeader(HttpHeaders.ACCEPT);
			if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.getWriter().write("{\"redirectUrl\":\"" + target.replace("\"", "%22") + "\"}");
			}
			else {
				response.sendRedirect(target);
			}
		};
	}
}
