package br.com.aquaqe.platform.identity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Consulta de contas por id, para os módulos que guardam referências a usuários (Owner, Technical Lead etc.). */
@Service
public class UserDirectory {

	private final JdbcClient jdbc;

	UserDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Contas encontradas, por id; ids inexistentes ficam de fora. */
	public Map<UUID, UserRef> find(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("select id, coalesce(name, username, subject) as name, active from identity.user_account where id in (:ids)")
			.param("ids", ids)
			.query((rs, n) -> new UserRef(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("active")))
			.list()
			.stream()
			.collect(Collectors.toMap(UserRef::id, Function.identity()));
	}

	/** Contas ativas entre as informadas cujo nome contém {@code query} (sem diferenciar maiúsculas), em ordem de nome. */
	public java.util.List<UserRef> search(Collection<UUID> ids, String query, int limit) {
		if (ids.isEmpty()) {
			return java.util.List.of();
		}
		String term = query == null ? "" : query.strip();
		return jdbc.sql("select id, name, active from (select id, coalesce(name, username, subject) as name, active"
				+ " from identity.user_account where id in (:ids) and active) u"
				+ " where cast(:term as text) = '' or u.name ilike '%' || cast(:term as text) || '%' order by u.name, u.id limit :limit")
			.param("ids", ids)
			.param("term", term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
			.param("limit", limit)
			.query((rs, n) -> new UserRef(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("active")))
			.list();
	}

	/**
	 * Diretório para a Administração (#32; REV-14): todas as contas cujo nome, usuário ou e-mail contém {@code query}, com
	 * o e-mail e a última atividade. Só para quem gere dados pessoais; as outras telas veem só o nome.
	 */
	public java.util.List<Account> directory(String query, int limit) {
		String term = query == null ? "" : query.strip();
		return jdbc.sql("""
				select id, coalesce(name, username, subject) as name, username, email, active,
				       subject like 'anonimizado:%' as anonymized, last_seen_at
				  from identity.user_account
				 where cast(:term as text) = '' or name ilike '%' || cast(:term as text) || '%'
				    or username ilike '%' || cast(:term as text) || '%' or email ilike '%' || cast(:term as text) || '%'
				 order by anonymized, name, id limit :limit
				""")
			.param("term", term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
			.param("limit", limit)
			.query((rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("username"),
				rs.getString("email"), rs.getBoolean("active"), rs.getBoolean("anonymized"),
				rs.getObject("last_seen_at", java.time.OffsetDateTime.class).toInstant()))
			.list();
	}

	/** Se a conta já foi anonimizada (#32). Vazio se a conta não existe. */
	public java.util.Optional<Boolean> anonymized(UUID id) {
		return jdbc.sql("select subject like 'anonimizado:%' from identity.user_account where id = :id")
			.param("id", id).query(Boolean.class).optional();
	}

	public record UserRef(UUID id, String name, boolean active) {
	}

	public record Account(UUID id, String name, String username, String email, boolean active, boolean anonymized,
			java.time.Instant lastSeenAt) {
	}
}
