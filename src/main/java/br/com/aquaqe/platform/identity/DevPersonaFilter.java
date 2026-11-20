package br.com.aquaqe.platform.identity;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.aquaqe.platform.identity.AuthProperties.DevPersona;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Modo {@code dev-persona}: autentica cada requisição como a persona guardada na sessão, ou a padrão (a primeira da
 * lista). Não há senha nem Keycloak; só existe fora de produção (ADR-17).
 */
final class DevPersonaFilter extends OncePerRequestFilter {

	static final String SESSION_ATTRIBUTE = "aquaqe.devPersona";

	private final AuthProperties auth;

	DevPersonaFilter(AuthProperties auth) {
		this.auth = auth;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new DevPersonaAuthentication(current(request)));
		SecurityContextHolder.setContext(context);
		try {
			chain.doFilter(request, response);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private DevPersona current(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		Object chosen = session == null ? null : session.getAttribute(SESSION_ATTRIBUTE);
		return Optional.ofNullable(chosen)
			.flatMap(username -> find(auth, username.toString()))
			.orElse(auth.devPersonas().getFirst());
	}

	static Optional<DevPersona> find(AuthProperties auth, String username) {
		return auth.devPersonas().stream().filter(p -> p.username().equals(username)).findFirst();
	}
}
