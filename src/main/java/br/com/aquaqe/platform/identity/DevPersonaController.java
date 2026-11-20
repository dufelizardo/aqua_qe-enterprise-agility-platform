package br.com.aquaqe.platform.identity;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.aquaqe.platform.identity.AuthProperties.DevPersona;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Troca de persona no modo {@code dev-persona} (ADR-17). Só existe nesse modo; em {@code oidc} os caminhos não existem
 * e respondem 404.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(name = "aquaqe.auth.mode", havingValue = "dev-persona")
class DevPersonaController {

	private final AuthProperties auth;

	DevPersonaController(AuthProperties auth) {
		this.auth = auth;
	}

	@GetMapping("/personas")
	PersonasResponse personas(Authentication authentication) {
		return new PersonasResponse(authentication.getName(), auth.devPersonas());
	}

	@PutMapping("/persona")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void choose(@RequestBody ChoosePersona body, HttpServletRequest request) {
		DevPersona persona = DevPersonaFilter.find(auth, body.username())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Persona desconhecida: " + body.username()));
		request.getSession(true).setAttribute(DevPersonaFilter.SESSION_ATTRIBUTE, persona.username());
	}

	record PersonasResponse(String current, List<DevPersona> personas) {
	}

	record ChoosePersona(String username) {
	}
}
