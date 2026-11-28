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

	/** Atribuições de papel vigentes exatamente na unidade (#35). */
	int activeAssignments(UUID orgUnitId) {
		return jdbc.sql("select count(*) from access.role_assignment where revoked_at is null and org_unit_id = :unit")
			.param("unit", orgUnitId)
			.query(Integer.class)
			.single();
	}

	/** Quem tem o papel vigente exatamente na unidade, do mais antigo no papel ao mais novo (#43). */
	List<UUID> holders(String roleKey, UUID orgUnitId) {
		return jdbc.sql("""
				select a.user_id from access.role_assignment a join access.role r on r.id = a.role_id
				 where a.revoked_at is null and r.key = :role and a.org_unit_id = :unit
				 group by a.user_id order by min(a.granted_at)
				""")
			.param("role", roleKey)
			.param("unit", orgUnitId)
			.query(UUID.class)
			.list();
	}

	boolean roleExists(String roleKey) {
		return jdbc.sql("select exists (select 1 from access.role where key = :key)")
			.param("key", roleKey)
			.query(Boolean.class)
			.single();
	}

	/** Verdadeiro se o usuário tem algum dos papéis em alguma das unidades. */
	/** Quem tem o papel vigente em alguma das unidades (#14). */
	List<UUID> holdersIn(String roleKey, Collection<UUID> orgUnitIds) {
		if (orgUnitIds.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				select distinct a.user_id from access.role_assignment a join access.role r on r.id = a.role_id
				 where a.revoked_at is null and r.key = :role and a.org_unit_id in (:units)
				""")
			.param("role", roleKey)
			.param("units", orgUnitIds)
			.query(UUID.class)
			.list();
	}

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
