package br.com.aquaqe.platform.identity;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/me} (SSD, seção 3.1). Nesta versão os dados vêm do token do provedor de identidade; a conta local e
 * as atribuições de papel entram com os módulos {@code identity} e {@code access}.
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

	@GetMapping
	MeResponse me(Authentication authentication) {
		Map<String, Object> claims = switch (authentication) {
			case JwtAuthenticationToken jwt -> jwt.getToken().getClaims();
			case DevPersonaAuthentication dev -> Map.of(
				"sub", "dev:" + dev.persona().username(),
				"preferred_username", dev.persona().username(),
				"name", dev.persona().name(),
				"email", dev.persona().email());
			default -> authentication.getPrincipal() instanceof OidcUser user ? user.getClaims() : Map.of();
		};
		return new MeResponse(
			text(claims, "sub"),
			text(claims, "preferred_username"),
			text(claims, "name"),
			text(claims, "email"),
			List.of());
	}

	private static String text(Map<String, Object> claims, String name) {
		Object value = claims.get(name);
		return value == null ? null : value.toString();
	}

	record MeResponse(String id, String username, String name, String email, List<Object> assignments) {
	}
}
