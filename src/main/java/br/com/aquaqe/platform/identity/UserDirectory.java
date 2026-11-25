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

	public record UserRef(UUID id, String name, boolean active) {
	}
}
