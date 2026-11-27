package br.com.aquaqe.platform.board;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.access.Permission;
import br.com.aquaqe.platform.board.BoardService.Column;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Catálogo de templates de quadro (#50; Proposta 05, MDL-07). Único na organização e visível para todos. Quem gere um
 * quadro salva as colunas dele como template; o administrador renomeia e remove. O template é aplicado por cópia ao
 * criar o quadro ({@link BoardService#create}): mudar ou remover o template não altera os quadros existentes.
 */
@Service
class BoardTemplates {

	static final int NAME_MAX = 120;
	static final int DESCRIPTION_MAX = 300;

	private final JdbcClient jdbc;
	private final ObjectMapper json;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final OrgUnits orgUnits;
	private final DomainEvents events;

	BoardTemplates(JdbcClient jdbc, ObjectMapper json, CurrentUser currentUser, Authorization authorization,
			OrgUnits orgUnits, DomainEvents events) {
		this.jdbc = jdbc;
		this.json = json;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.orgUnits = orgUnits;
		this.events = events;
	}

	@Transactional(readOnly = true)
	List<Template> list(String level) {
		currentUser.require();
		return jdbc.sql("""
				select id, name, level, description, columns::text as columns, builtin, updated_at from board.board_template
				 where cast(:level as varchar) is null or level = cast(:level as varchar)
				 order by case level when 'FL3' then 1 when 'FL2' then 2 else 3 end, builtin desc, lower(name)
				""")
			.param("level", level)
			.query((rs, n) -> new Template(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("level"),
				rs.getString("description"), columns(rs.getString("columns")), rs.getBoolean("builtin"),
				rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	Optional<Template> find(UUID id) {
		return jdbc.sql("""
				select id, name, level, description, columns::text as columns, builtin, updated_at from board.board_template
				 where id = :id
				""")
			.param("id", id)
			.query((rs, n) -> new Template(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("level"),
				rs.getString("description"), columns(rs.getString("columns")), rs.getBoolean("builtin"),
				rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
			.optional();
	}

	/** Grava as colunas de um quadro como template; a permissão (gerir o quadro) é verificada por quem chama. */
	Template insert(String name, String level, String description, List<Column> columns, UUID user, UUID orgUnitId,
			UUID boardId) {
		String cleanName = name(name);
		String cleanDescription = description(description);
		List<TemplateColumn> copy = columns.stream()
			.map(c -> new TemplateColumn(c.name(), c.phase(), c.statusKeys(), c.wipLimit(), c.wipPolicy()))
			.toList();
		UUID id = UUID.randomUUID();
		try {
			jdbc.sql("""
					insert into board.board_template (id, name, level, description, columns, created_by)
					values (:id, :name, :level, :description, cast(:columns as jsonb), :user)
					""")
				.param("id", id).param("name", cleanName).param("level", level).param("description", cleanDescription)
				.param("columns", json.writeValueAsString(copy)).param("user", user)
				.update();
		} catch (DuplicateKeyException e) {
			throw ApiException.validation("name", "já existe um template de " + level + " com este nome");
		}
		events.publish("board_template.created", user, orgUnitId, new Aggregate("BOARD_TEMPLATE", id, cleanName),
			Map.of("templateId", id.toString(), "level", level, "fromBoardId", boardId.toString()));
		return find(id).orElseThrow();
	}

	/** Renomeia e altera a descrição. Só o administrador. As colunas não mudam: para isso, salve outro quadro. */
	@Transactional
	Template update(UUID id, Map<String, Object> body) {
		UUID user = requireAdmin();
		Template current = find(id).orElseThrow(ApiException::notFound);
		String name = body != null && body.containsKey("name") ? name(asText(body.get("name"))) : current.name();
		String description = body != null && body.containsKey("description")
			? description(asText(body.get("description"))) : current.description();
		try {
			jdbc.sql("update board.board_template set name = :name, description = :description, updated_at = now() where id = :id")
				.param("id", id).param("name", name).param("description", description)
				.update();
		} catch (DuplicateKeyException e) {
			throw ApiException.validation("name", "já existe um template de " + current.level() + " com este nome");
		}
		events.publish("board_template.updated", user, orgUnits.rootId(), new Aggregate("BOARD_TEMPLATE", id, name),
			Map.of("templateId", id.toString()), Map.of("name", current.name()), Map.of("name", name));
		return find(id).orElseThrow();
	}

	/** Remove do catálogo. Os quadros criados a partir dele não mudam. Só o administrador. */
	@Transactional
	void remove(UUID id) {
		UUID user = requireAdmin();
		Template current = find(id).orElseThrow(ApiException::notFound);
		jdbc.sql("delete from board.board_template where id = :id").param("id", id).update();
		events.publish("board_template.removed", user, orgUnits.rootId(),
			new Aggregate("BOARD_TEMPLATE", id, current.name()), Map.of("templateId", id.toString()));
	}

	// ---------------------------------------------------------------------------------------------- apoio

	private UUID requireAdmin() {
		UUID user = currentUser.require().id();
		if (!authorization.can(user, Permission.MANAGE_CONFIGURATION, orgUnits.rootId())) {
			throw ApiException.forbidden();
		}
		return user;
	}

	private List<TemplateColumn> columns(String text) {
		return json.readValue(text, new TypeReference<List<TemplateColumn>>() {
		});
	}

	private static String asText(Object value) {
		return value instanceof String s ? s : null;
	}

	private static String name(String name) {
		String clean = name == null ? "" : name.strip();
		if (clean.isEmpty() || clean.length() > NAME_MAX) {
			throw ApiException.validation("name", "obrigatório, até " + NAME_MAX + " caracteres");
		}
		return clean;
	}

	private static String description(String description) {
		String clean = description == null ? null : description.strip();
		if (clean != null && clean.length() > DESCRIPTION_MAX) {
			throw ApiException.validation("description", "até " + DESCRIPTION_MAX + " caracteres");
		}
		return clean == null || clean.isEmpty() ? null : clean;
	}

	record TemplateColumn(String name, String phase, List<String> statusKeys, Integer wipLimit, String wipPolicy) {
	}

	/** Template do catálogo; {@code builtin} para os que vêm com a instalação. */
	record Template(UUID id, String name, String level, String description, List<TemplateColumn> columns,
			boolean builtin, Instant updatedAt) {
	}
}
