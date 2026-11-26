package br.com.aquaqe.platform.activity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.identity.UserDirectory;
import br.com.aquaqe.platform.identity.UserDirectory.UserRef;
import br.com.aquaqe.platform.livingdocs.Pages;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.organization.OrgUnits.OrgUnitSummary;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Home (#83): "Continuar de onde parou" e "Novidades".
 * <ul>
 * <li><b>Recentes</b>: a tela avisa o que a pessoa abriu (artefato, documento do artefato ou página do Wiki). Na leitura,
 * cada item é conferido de novo com as regras de leitura; o que deixou de ser legível, ou foi arquivado, não aparece.</li>
 * <li><b>Novidades</b>: o último evento de cada artefato, documento ou página nas unidades em que a pessoa lê tudo
 * (SSD 2.1, regra 1), nos últimos {@value #NEWS_DAYS} dias.</li>
 * </ul>
 */
@Service
class ActivityService {

	static final int RECENT_LIMIT = 10;
	/** Quantas visitas ficam guardadas por pessoa; as mais antigas são apagadas. */
	static final int RECENT_KEPT = 30;
	static final int NEWS_LIMIT = 20;
	static final int NEWS_DAYS = 30;
	static final int FAVORITES_LIMIT = 50;
	static final Set<String> DOCUMENT_TYPES = Set.of("PRD", "SAD", "SSD", "UX", "QA");

	private final JdbcClient jdbc;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final Artifacts artifacts;
	private final Pages pages;
	private final OrgUnits orgUnits;
	private final UserDirectory users;

	ActivityService(JdbcClient jdbc, CurrentUser currentUser, Authorization authorization, Artifacts artifacts,
			Pages pages, OrgUnits orgUnits, UserDirectory users) {
		this.jdbc = jdbc;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.artifacts = artifacts;
		this.pages = pages;
		this.orgUnits = orgUnits;
		this.users = users;
	}

	/** Registra que a pessoa abriu o item. {@code 404} se ela não pode lê-lo; {@code 400} se o pedido é inválido. */
	@Transactional
	void visit(Map<String, Object> body) {
		UUID user = currentUser.require().id();
		String type = text(body, "type");
		String ref = text(body, "ref");
		String normalized = switch (type) {
			case "ARTIFACT" -> artifacts.readable(ref).key();
			case "DOCUMENT" -> document(ref);
			case "PAGE" -> pages.readable(uuid(ref)).orElseThrow(ApiException::notFound).id().toString();
			default -> throw ApiException.validation("type", "use ARTIFACT, DOCUMENT ou PAGE");
		};
		jdbc.sql("""
				insert into activity.recent_visit (user_id, target_type, ref, visited_at) values (:user, :type, :ref, now())
				on conflict (user_id, target_type, ref) do update set visited_at = now()
				""")
			.param("user", user).param("type", type).param("ref", normalized).update();
		jdbc.sql("""
				delete from activity.recent_visit
				 where user_id = :user
				   and (target_type, ref) not in (select target_type, ref from activity.recent_visit where user_id = :user
				                                   order by visited_at desc limit :kept)
				""")
			.param("user", user).param("kept", RECENT_KEPT).update();
	}

	/** Itens abertos por último, do mais recente para o mais antigo, só os que a pessoa ainda lê. */
	@Transactional(readOnly = true)
	List<RecentItem> recent() {
		UUID user = currentUser.require().id();
		List<Visit> visits = jdbc.sql("""
				select target_type, ref, visited_at from activity.recent_visit where user_id = :user
				 order by visited_at desc limit :kept
				""")
			.param("user", user).param("kept", RECENT_KEPT)
			.query((rs, n) -> new Visit(rs.getString("target_type"), rs.getString("ref"),
				rs.getObject("visited_at", OffsetDateTime.class).toInstant()))
			.list();
		Map<UUID, Unit> units = new HashMap<>();
		List<RecentItem> items = new ArrayList<>();
		for (Visit visit : visits) {
			resolve(visit, units).ifPresent(items::add);
			if (items.size() == RECENT_LIMIT) {
				break;
			}
		}
		return items;
	}

	/** O último evento de cada item nas unidades que a pessoa lê, do mais recente para o mais antigo. */
	@Transactional(readOnly = true)
	List<NewsItem> news() {
		UUID user = currentUser.require().id();
		Set<UUID> scope = authorization.readScope(user);
		if (scope.isEmpty()) {
			return List.of();
		}
		List<Event> events = jdbc.sql("""
				select * from (
				  select distinct on (target_id) occurred_at, org_unit_id, actor_id, action, target_type, target_id, target_key
				    from activity.unit_event
				   where org_unit_id in (:units) and occurred_at > now() - make_interval(days => :days)
				   order by target_id, occurred_at desc
				) latest order by occurred_at desc limit :limit
				""")
			.param("units", scope).param("days", NEWS_DAYS).param("limit", NEWS_LIMIT)
			.query((rs, n) -> new Event(rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
				rs.getObject("org_unit_id", UUID.class), rs.getObject("actor_id", UUID.class), rs.getString("action"),
				rs.getString("target_type"), rs.getObject("target_id", UUID.class), rs.getString("target_key")))
			.list();
		Map<UUID, UserRef> people = users.find(events.stream().map(Event::actorId).filter(Objects::nonNull)
			.collect(Collectors.toSet()));
		Map<UUID, Unit> units = new HashMap<>();
		List<NewsItem> items = new ArrayList<>();
		for (Event e : events) {
			Person actor = e.actorId() == null ? null
					: new Person(e.actorId(), people.containsKey(e.actorId()) ? people.get(e.actorId()).name() : null);
			Unit unit = unit(e.orgUnitId(), units);
			boolean own = user.equals(e.actorId());
			switch (e.targetType()) {
				case "ARTIFACT" -> {
					ArtifactView view = artifacts.view(e.targetId());
					items.add(new NewsItem(e.occurredAt(), e.action(), "ARTIFACT", view.key(), view.title(), view.key(),
						null, null, unit, actor, own));
				}
				case "DOCUMENT" -> {
					String[] parts = splitDocument(e.targetKey());
					items.add(new NewsItem(e.occurredAt(), e.action(), "DOCUMENT", e.targetKey(), null, parts[0], parts[1],
						null, unit, actor, own));
				}
				default -> items.add(new NewsItem(e.occurredAt(), e.action(), "PAGE", e.targetId().toString(),
					e.targetKey(), null, null, e.targetId(), unit, actor, own));
			}
		}
		return items;
	}

	/** Marca a página com estrela (#85). {@code 404} se a pessoa não lê a página. */
	@Transactional
	void favorite(UUID pageId) {
		UUID user = currentUser.require().id();
		pages.readable(pageId).orElseThrow(ApiException::notFound);
		jdbc.sql("""
				insert into activity.favorite (user_id, target_type, ref) values (:user, 'PAGE', :ref)
				on conflict do nothing
				""")
			.param("user", user).param("ref", pageId.toString()).update();
	}

	/** Tira a estrela; sem efeito se a página não estava marcada. */
	@Transactional
	void unfavorite(UUID pageId) {
		UUID user = currentUser.require().id();
		jdbc.sql("delete from activity.favorite where user_id = :user and target_type = 'PAGE' and ref = :ref")
			.param("user", user).param("ref", pageId.toString()).update();
	}

	/** Páginas com estrela que a pessoa ainda lê, das marcadas por último às mais antigas. */
	@Transactional(readOnly = true)
	List<FavoriteItem> favorites() {
		UUID user = currentUser.require().id();
		List<String> refs = jdbc.sql("""
				select ref from activity.favorite where user_id = :user and target_type = 'PAGE'
				 order by created_at desc limit :limit
				""")
			.param("user", user).param("limit", FAVORITES_LIMIT)
			.query(String.class).list();
		Map<UUID, Unit> units = new HashMap<>();
		List<FavoriteItem> items = new ArrayList<>();
		for (String ref : refs) {
			pages.readable(UUID.fromString(ref))
				.ifPresent(page -> items.add(new FavoriteItem("PAGE", page.id(), page.title(), page.type(),
					unit(page.orgUnitId(), units))));
		}
		return items;
	}

	private Optional<RecentItem> resolve(Visit visit, Map<UUID, Unit> units) {
		try {
			if ("PAGE".equals(visit.type())) {
				return pages.readable(UUID.fromString(visit.ref()))
					.map(page -> new RecentItem("PAGE", visit.ref(), page.title(), null, null, page.id(),
						unit(page.orgUnitId(), units), visit.visitedAt()));
			}
			String[] parts = "DOCUMENT".equals(visit.type()) ? splitDocument(visit.ref())
					: new String[] { visit.ref(), null };
			ArtifactRef ref = artifacts.readable(parts[0]);
			if (ref.archived()) {
				return Optional.empty();
			}
			ArtifactView view = artifacts.view(ref.id());
			return Optional.of(new RecentItem(visit.type(), visit.ref(), view.title(), view.key(), parts[1], null,
				unit(ref.orgUnitId(), units), visit.visitedAt()));
		} catch (ApiException | IllegalArgumentException notReadable) {
			return Optional.empty();
		}
	}

	private String document(String ref) {
		String[] parts = splitDocument(ref);
		if (parts[1] == null || !DOCUMENT_TYPES.contains(parts[1])) {
			throw ApiException.validation("ref", "use a chave do artefato e o tipo do documento, ex.: INIT-1/PRD");
		}
		return artifacts.readable(parts[0]).key() + "/" + parts[1];
	}

	/** {@code INIT-1/PRD} vira {@code [INIT-1, PRD]}; sem barra, o tipo fica nulo. */
	static String[] splitDocument(String ref) {
		int slash = ref == null ? -1 : ref.lastIndexOf('/');
		return slash < 0 ? new String[] { ref, null } : new String[] { ref.substring(0, slash), ref.substring(slash + 1) };
	}

	private Unit unit(UUID id, Map<UUID, Unit> cache) {
		if (id == null) {
			return null;
		}
		return cache.computeIfAbsent(id, key -> new Unit(key, orgUnits.find(key).map(OrgUnitSummary::name).orElse(null)));
	}

	private static String text(Map<String, Object> body, String field) {
		Object value = body == null ? null : body.get(field);
		if (!(value instanceof String s) || s.isBlank() || s.length() > 200) {
			throw ApiException.validation(field, "obrigatório, até 200 caracteres");
		}
		return s.strip();
	}

	private static UUID uuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw ApiException.validation("ref", "use o id da página");
		}
	}

	private record Visit(String type, String ref, Instant visitedAt) {
	}

	private record Event(Instant occurredAt, UUID orgUnitId, UUID actorId, String action, String targetType,
			UUID targetId, String targetKey) {
	}

	record Unit(UUID id, String name) {
	}

	/** Página com estrela: {@code pageType} é PAGE ou REGIMENTO. */
	record FavoriteItem(String type, UUID pageId, String title, String pageType, Unit orgUnit) {
	}

	record Person(UUID id, String name) {
	}

	/**
	 * Item aberto por último. {@code type}: ARTIFACT, DOCUMENT ou PAGE. Artefato e documento trazem {@code artifactKey}
	 * (e o documento, {@code documentType}); a página traz {@code pageId}. {@code title}: o título atual.
	 */
	record RecentItem(String type, String ref, String title, String artifactKey, String documentType, UUID pageId,
			Unit orgUnit, Instant visitedAt) {
	}

	/**
	 * O que mudou: {@code action} é o tipo do evento (SSD 7.2), como {@code artifact.status_changed} ou
	 * {@code knowledge_page.updated}; {@code own} diz se foi a própria pessoa.
	 */
	record NewsItem(Instant occurredAt, String action, String type, String ref, String title, String artifactKey,
			String documentType, UUID pageId, Unit orgUnit, Person actor, boolean own) {
	}
}
