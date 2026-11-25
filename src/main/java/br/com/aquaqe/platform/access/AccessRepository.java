package br.com.aquaqe.platform.access;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Acesso às tabelas do schema {@code access}. */
@Repository
class AccessRepository {

	private final JdbcClient jdbc;

	AccessRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Atribuições vigentes do usuário. */
	List<ActiveAssignment> assignments(UUID userId) {
		return jdbc.sql("""
				select a.id, r.key as role, a.org_unit_id from access.role_assignment a
				  join access.role r on r.id = a.role_id
				 where a.user_id = :user and a.revoked_at is null
				 order by a.granted_at, a.id
				""")
			.param("user", userId)
			.query((rs, n) -> new ActiveAssignment(rs.getObject("id", UUID.class), rs.getString("role"),
				rs.getObject("org_unit_id", UUID.class)))
			.list();
	}

	/** Usuários distintos com papel vigente em alguma das unidades. */
	List<UUID> usersIn(Collection<UUID> orgUnitIds) {
		return jdbc.sql("""
				select distinct a.user_id from access.role_assignment a
				 where a.revoked_at is null and a.org_unit_id in (:units)
				""")
			.param("units", orgUnitIds)
			.query(UUID.class)
			.list();
	}

	/** Verdadeiro se o usuário tem algum dos papéis em alguma das unidades. */
	boolean hasAnyRole(UUID userId, Collection<String> roles, Collection<UUID> orgUnitIds) {
		if (roles.isEmpty() || orgUnitIds.isEmpty()) {
			return false;
		}
		return jdbc.sql("""
				select exists (select 1 from access.role_assignment a join access.role r on r.id = a.role_id
				 where a.user_id = :user and a.revoked_at is null and r.key in (:roles) and a.org_unit_id in (:units))
				""")
			.param("user", userId)
			.param("roles", roles)
			.param("units", orgUnitIds)
			.query(Boolean.class)
			.single();
	}

	void grant(UUID userId, String roleKey, UUID orgUnitId, UUID grantedBy) {
		jdbc.sql("""
				insert into access.role_assignment (user_id, role_id, org_unit_id, granted_by)
				select :user, r.id, :unit, :by from access.role r where r.key = :role
				on conflict do nothing
				""")
			.param("user", userId)
			.param("role", roleKey)
			.param("unit", orgUnitId)
			.param("by", grantedBy)
			.update();
	}

	record ActiveAssignment(UUID id, String role, UUID orgUnitId) {
	}
}
