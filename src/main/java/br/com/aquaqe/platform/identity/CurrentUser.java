package br.com.aquaqe.platform.identity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import br.com.aquaqe.platform.shared.ApiException;

/**
 * Conta do usuário da requisição. Cria a conta no primeiro acesso (SSD, seção 1.2) e publica
 * {@link UserAccountCreated}; nos acessos seguintes, atualiza nome e e-mail vindos do IdP.
 *
 * <p>A gravação da conta roda numa transação própria, de escrita: a rota pode estar numa transação somente leitura
 * (os painéis e listas com {@code readOnly = true}) e o primeiro acesso de um usuário pode ser justamente ela (#95).
 */
@Service
public class CurrentUser {

	private static final String CACHE = CurrentUser.class.getName();

	private final JdbcClient jdbc;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate write;

	CurrentUser(JdbcClient jdbc, ApplicationEventPublisher events, PlatformTransactionManager transactions) {
		this.jdbc = jdbc;
		this.events = events;
		this.write = new TransactionTemplate(transactions);
		this.write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** @throws ApiException {@code UNAUTHENTICATED} se a requisição não tem usuário autenticado */
	@Transactional
	public UserAccount require() {
		RequestAttributes request = RequestContextHolder.getRequestAttributes();
		if (request != null && request.getAttribute(CACHE, RequestAttributes.SCOPE_REQUEST) instanceof UserAccount cached) {
			return cached;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Map<String, Object> claims = claims(authentication);
		String subject = text(claims, "sub");
		if (subject == null) {
			throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
				"Autenticação necessária.");
		}
		UserAccount account = upsert(subject, text(claims, "preferred_username"), text(claims, "name"),
			text(claims, "email"));
		if (request != null) {
			request.setAttribute(CACHE, account, RequestAttributes.SCOPE_REQUEST);
		}
		return account;
	}

	private UserAccount upsert(String subject, String username, String name, String email) {
		Optional<UserAccount> existing = find(subject);
		if (existing.isPresent()) {
			UserAccount found = existing.get();
			if (!Objects.equals(found.username(), username) || !Objects.equals(found.name(), name)
					|| !Objects.equals(found.email(), email)) {
				write.executeWithoutResult(status -> jdbc.sql("""
						update identity.user_account set username = :username, name = :name, email = :email,
						       last_seen_at = now() where id = :id
						""")
					.param("id", found.id()).param("username", username).param("name", name).param("email", email)
					.update());
				return new UserAccount(found.id(), subject, username, name, email);
			}
			return found;
		}
		return write.execute(status -> create(subject, username, name, email));
	}

	private UserAccount create(String subject, String username, String name, String email) {
		Optional<UUID> created = jdbc.sql("""
				insert into identity.user_account (subject, username, name, email)
				values (:subject, :username, :name, :email)
				on conflict (subject) do nothing returning id
				""")
			.param("subject", subject).param("username", username).param("name", name).param("email", email)
			.query(UUID.class)
			.optional();
		if (created.isEmpty()) {
			// Outra requisição do mesmo usuário criou a conta ao mesmo tempo.
			return find(subject).orElseThrow();
		}
		UserAccount account = new UserAccount(created.get(), subject, username, name, email);
		events.publishEvent(new UserAccountCreated(account));
		return account;
	}

	private Optional<UserAccount> find(String subject) {
		return jdbc.sql("select id, subject, username, name, email from identity.user_account where subject = :subject")
			.param("subject", subject)
			.query((rs, n) -> new UserAccount(rs.getObject("id", UUID.class), rs.getString("subject"),
				rs.getString("username"), rs.getString("name"), rs.getString("email")))
			.optional();
	}

	static Map<String, Object> claims(Authentication authentication) {
		return switch (authentication) {
			case null -> Map.of();
			case JwtAuthenticationToken jwt -> jwt.getToken().getClaims();
			case DevPersonaAuthentication dev -> Map.of(
				"sub", "dev:" + dev.persona().username(),
				"preferred_username", dev.persona().username(),
				"name", dev.persona().name(),
				"email", dev.persona().email());
			default -> authentication.getPrincipal() instanceof OidcUser user ? user.getClaims() : Map.of();
		};
	}

	private static String text(Map<String, Object> claims, String name) {
		Object value = claims.get(name);
		return value == null ? null : value.toString();
	}
}
