package br.com.aquaqe.platform.livingdocs;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.events.DomainEvents;
import br.com.aquaqe.platform.events.EventEnvelope.Aggregate;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.shared.ApiException;

/**
 * Comentários nas páginas do Wiki e do Regimento (#85; Proposta 07, Fase D, KNOW-024 e 025).
 * <ul>
 * <li>Quem lê a página comenta e responde (um nível de resposta). "Comentar trecho" guarda o texto selecionado.</li>
 * <li>Menções: só pessoas que leem a página, isto é, com papel na unidade dela, nas de cima ou nas de baixo.</li>
 * <li>Cada comentário publica {@code knowledge_page.commented}, com as pessoas mencionadas e o autor do comentário
 * respondido; o módulo {@code notification} cria os avisos a partir desse evento.</li>
 * <li>Só o autor exclui o próprio comentário; com respostas, ele aparece como "excluído" para manter a conversa.</li>
 * </ul>
 */
@Service
class CommentService {

	static final int BODY_MAX = 2000;
	static final int QUOTE_MAX = 500;
	static final int MAX_MENTIONS = 20;
	static final int PEOPLE_LIMIT = 20;
	static final int EXCERPT = 140;

	private final JdbcClient jdbc;
	private final PageService pages;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final OrgUnits orgUnits;
	private final UserDirectory users;
	private final DomainEvents events;

	CommentService(JdbcClient jdbc, PageService pages, CurrentUser currentUser, Authorization authorization,
			OrgUnits orgUnits, UserDirectory users, DomainEvents events) {
		this.jdbc = jdbc;
		this.pages = pages;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.orgUnits = orgUnits;
		this.users = users;
		this.events = events;
	}

	@Transactional(readOnly = true)
	List<CommentView> list(UUID pageId) {
		UUID user = currentUser.require().id();
		pages.ref(pageId);
		List<Row> rows = rows(pageId);
		Set<UUID> withReplies = rows.stream().map(Row::parentId).filter(Objects::nonNull).collect(Collectors.toSet());
		Set<UUID> people = new HashSet<>();
		rows.forEach(r -> {
			people.add(r.authorId());
			people.addAll(r.mentions());
		});
		Map<UUID, UserRef> names = users.find(people);
		List<CommentView> result = new ArrayList<>();
		for (Row r : rows) {
			if (r.deletedAt() != null && !withReplies.contains(r.id())) {
				continue;
			}
			result.add(view(r, names, user));
		}
		return result;
	}

	@Transactional
	CommentView create(UUID pageId, Map<String, Object> body) {
		UUID user = currentUser.require().id();
		Pages.PageRef page = pages.ref(pageId);
		String text = text(body, "body", BODY_MAX, true);
		String quote = text(body, "quote", QUOTE_MAX, false);
		UUID parentId = uuid(body, "parentId");
		UUID replyTo = null;
		if (parentId != null) {
			Row parent = find(parentId).filter(p -> p.documentId().equals(pageId))
				.orElseThrow(() -> ApiException.validation("parentId", "comentário não encontrado nesta página"));
			if (parent.parentId() != null) {
				parentId = parent.parentId();
				parent = find(parentId).orElseThrow();
			}
			replyTo = parent.authorId();
		}
		List<UUID> mentions = mentions(body, page);
		UUID id = jdbc.sql("""
				insert into livingdocs.page_comment (document_id, parent_id, author_id, body, quote, mentions)
				values (:page, :parent, :author, :body, :quote, cast(:mentions as uuid[]))
				returning id
				""")
			.param("page", pageId).param("parent", parentId).param("author", user).param("body", text)
			.param("quote", quote).param("mentions", mentions.toArray(UUID[]::new))
			.query(UUID.class).single();
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("commentId", id.toString());
		payload.put("title", page.title());
		payload.put("excerpt", text.length() <= EXCERPT ? text : text.substring(0, EXCERPT) + "…");
		payload.put("mentionedUserIds", mentions.stream().map(UUID::toString).toList());
		if (replyTo != null) {
			payload.put("replyToUserId", replyTo.toString());
		}
		events.publish("knowledge_page.commented", user, page.orgUnitId(),
			new Aggregate("KNOWLEDGE_PAGE", page.id(), page.title()), payload);
		Row created = find(id).orElseThrow();
		Set<UUID> people = new HashSet<>(mentions);
		people.add(user);
		return view(created, users.find(people), user);
	}

	@Transactional
	void delete(UUID pageId, UUID commentId) {
		UUID user = currentUser.require().id();
		pages.ref(pageId);
		Row row = find(commentId).filter(r -> r.documentId().equals(pageId) && r.deletedAt() == null)
			.orElseThrow(ApiException::notFound);
		if (!row.authorId().equals(user)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Só quem escreveu o comentário pode excluí-lo.");
		}
		jdbc.sql("update livingdocs.page_comment set deleted_at = now() where id = :id").param("id", commentId).update();
	}

	/** Pessoas que podem ser mencionadas: as que leem a página (papel na unidade, nas de cima ou nas de baixo). */
	@Transactional(readOnly = true)
	List<UserRef> people(UUID pageId, String query) {
		currentUser.require();
		Pages.PageRef page = pages.ref(pageId);
		return users.search(mentionable(page), query, PEOPLE_LIMIT);
	}

	private Set<UUID> mentionable(Pages.PageRef page) {
		Set<UUID> units = new LinkedHashSet<>(orgUnits.lineage(page.orgUnitId()));
		units.addAll(orgUnits.subtrees(List.of(page.orgUnitId())));
		return authorization.peopleIn(units);
	}

	private List<UUID> mentions(Map<String, Object> body, Pages.PageRef page) {
		Object value = body.get("mentionIds");
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof List<?> list) || list.size() > MAX_MENTIONS) {
			throw ApiException.validation("mentionIds", "lista de até " + MAX_MENTIONS + " pessoas");
		}
		Set<UUID> ids = new LinkedHashSet<>();
		for (Object item : list) {
			try {
				ids.add(UUID.fromString(String.valueOf(item)));
			} catch (IllegalArgumentException e) {
				throw ApiException.validation("mentionIds", "use os ids das pessoas");
			}
		}
		if (ids.isEmpty()) {
			return List.of();
		}
		if (!mentionable(page).containsAll(ids)) {
			throw ApiException.validation("mentionIds", "só dá para mencionar quem lê esta página");
		}
		return List.copyOf(ids);
	}

	private List<Row> rows(UUID pageId) {
		return jdbc.sql("""
				select id, document_id, parent_id, author_id, body, quote, mentions, created_at, deleted_at
				  from livingdocs.page_comment where document_id = :page
				 order by coalesce((select p.created_at from livingdocs.page_comment p where p.id = page_comment.parent_id),
				                   created_at), coalesce(parent_id, id), parent_id nulls first, created_at
				""")
			.param("page", pageId)
			.query((rs, n) -> row(rs))
			.list();
	}

	private Optional<Row> find(UUID id) {
		return jdbc.sql("""
				select id, document_id, parent_id, author_id, body, quote, mentions, created_at, deleted_at
				  from livingdocs.page_comment where id = :id
				""")
			.param("id", id)
			.query((rs, n) -> row(rs))
			.optional();
	}

	private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
		java.sql.Array mentions = rs.getArray("mentions");
		List<UUID> ids = mentions == null ? List.of()
				: Arrays.stream((Object[]) mentions.getArray()).map(o -> (UUID) o).toList();
		OffsetDateTime deleted = rs.getObject("deleted_at", OffsetDateTime.class);
		return new Row(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
			rs.getObject("parent_id", UUID.class), rs.getObject("author_id", UUID.class), rs.getString("body"),
			rs.getString("quote"), ids, rs.getObject("created_at", OffsetDateTime.class).toInstant(),
			deleted == null ? null : deleted.toInstant());
	}

	private static CommentView view(Row r, Map<UUID, UserRef> names, UUID user) {
		boolean deleted = r.deletedAt() != null;
		Person author = new Person(r.authorId(), names.containsKey(r.authorId()) ? names.get(r.authorId()).name() : null);
		List<Person> mentions = deleted ? List.of()
				: r.mentions().stream().map(id -> new Person(id, names.containsKey(id) ? names.get(id).name() : null))
					.toList();
		return new CommentView(r.id(), r.parentId(), author, deleted ? null : r.body(), deleted ? null : r.quote(),
			mentions, r.createdAt(), deleted, !deleted && r.authorId().equals(user));
	}

	private static String text(Map<String, Object> body, String field, int max, boolean required) {
		Object value = body == null ? null : body.get(field);
		if (value == null || (value instanceof String s && s.isBlank())) {
			if (required) {
				throw ApiException.validation(field, "obrigatório");
			}
			return null;
		}
		if (!(value instanceof String s) || s.strip().length() > max) {
			throw ApiException.validation(field, "até " + max + " caracteres");
		}
		return s.strip();
	}

	private static UUID uuid(Map<String, Object> body, String field) {
		Object value = body == null ? null : body.get(field);
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(String.valueOf(value));
		} catch (IllegalArgumentException e) {
			throw ApiException.validation(field, "id inválido");
		}
	}

	private record Row(UUID id, UUID documentId, UUID parentId, UUID authorId, String body, String quote,
			List<UUID> mentions, Instant createdAt, Instant deletedAt) {
	}

	record Person(UUID id, String name) {
	}

	/**
	 * Comentário: {@code parentId} nulo para o principal; excluído com respostas vem sem texto ({@code deleted});
	 * {@code canDelete} só para o autor.
	 */
	record CommentView(UUID id, UUID parentId, Person author, String body, String quote, List<Person> mentions,
			Instant createdAt, boolean deleted, boolean canDelete) {
	}
}
