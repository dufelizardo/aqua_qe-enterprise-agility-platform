package br.com.aquaqe.platform.livingdocs;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import br.com.aquaqe.platform.access.Authorization;
import br.com.aquaqe.platform.identity.CurrentUser;
import br.com.aquaqe.platform.shared.TextSearch;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.Artifacts;

/**
 * Busca textual (#84; ADR-11) nas páginas do Wiki e do Regimento e nos documentos do artefato, com as mesmas regras de
 * leitura de {@code GET /pages/{id}} e {@code GET /artifacts/{a}/documents/{type}}:
 * <ul>
 * <li>página: na unidade que a pessoa lê tudo (regras 1 a 7), a versão atual; nas unidades acima das dela (regra 9), só
 * a versão publicada; arquivadas ficam de fora;</li>
 * <li>documento do artefato: a versão atual, se a pessoa lê o artefato e ele não está arquivado.</li>
 * </ul>
 * O texto de uma versão é a junção de todos os campos {@code text} das seções (parágrafos, títulos, itens de lista e o
 * conteúdo do editor contínuo).
 */
@Service
public class DocumentSearch {

	/** Candidatos lidos do banco antes do filtro de leitura do artefato. */
	private static final int CANDIDATES = 200;
	private static final String TEXT = """
			(select coalesce(string_agg(t #>> '{}', ' '), '') from jsonb_path_query(v.sections, 'lax $.**.text') t)
			""";

	private final JdbcClient jdbc;
	private final CurrentUser currentUser;
	private final Authorization authorization;
	private final Artifacts artifacts;

	DocumentSearch(JdbcClient jdbc, CurrentUser currentUser, Authorization authorization, Artifacts artifacts) {
		this.jdbc = jdbc;
		this.currentUser = currentUser;
		this.authorization = authorization;
		this.artifacts = artifacts;
	}

	/** Páginas legíveis com todos os termos no título ou no texto; primeiro as que têm os termos no título. */
	public List<PageHit> pages(List<String> terms, int limit) {
		UUID user = currentUser.require().id();
		Set<UUID> full = authorization.readScope(user);
		Set<UUID> published = new HashSet<>(authorization.knowledgeScope(user));
		published.removeAll(full);
		if (full.isEmpty() && published.isEmpty()) {
			return List.of();
		}
		TextSearch.Condition all = TextSearch.allTerms("d.title || ' ' || x.text", terms, "t");
		TextSearch.Condition heading = TextSearch.allTerms("d.title", terms, "t");
		return jdbc.sql("""
				select d.id, d.org_unit_id, d.type, d.title, d.updated_at, x.text
				  from livingdocs.document d
				  join livingdocs.document_version v on v.document_id = d.id
				   and v.number = case when d.org_unit_id = any(cast(:full as uuid[])) then d.current_version
				                       else d.published_version end
				  cross join lateral (select """ + TEXT + """
				 as text) x
				 where d.owner_type = 'ORG_UNIT' and d.archived_at is null
				   and (d.org_unit_id = any(cast(:full as uuid[]))
				        or (d.org_unit_id = any(cast(:published as uuid[])) and d.published_version is not null))
				   and """ + all.sql() + " order by " + heading.sql() + " desc, d.updated_at desc limit :limit")
			.params(all.params())
			.param("full", full.toArray(UUID[]::new))
			.param("published", published.toArray(UUID[]::new))
			.param("limit", limit)
			.query((rs, n) -> new PageHit(rs.getObject("id", UUID.class), rs.getObject("org_unit_id", UUID.class),
				rs.getString("type"), rs.getString("title"), rs.getString("text"), instant(rs.getObject("updated_at",
					OffsetDateTime.class))))
			.list();
	}

	/** Documentos de artefatos legíveis com todos os termos no texto da versão atual, dos mais recentes. */
	public List<DocumentHit> artifactDocuments(List<String> terms, int limit) {
		currentUser.require();
		TextSearch.Condition all = TextSearch.allTerms("x.text", terms, "t");
		List<Candidate> candidates = jdbc.sql("""
				select d.artifact_id, d.type, d.status, d.updated_at, x.text
				  from livingdocs.document d
				  join livingdocs.document_version v on v.document_id = d.id and v.number = d.current_version
				  cross join lateral (select """ + TEXT + """
				 as text) x
				 where d.owner_type = 'ARTIFACT' and d.archived_at is null and """ + all.sql()
				+ " order by d.updated_at desc limit :limit")
			.params(all.params())
			.param("limit", CANDIDATES)
			.query((rs, n) -> new Candidate(rs.getObject("artifact_id", UUID.class), rs.getString("type"),
				rs.getString("status"), rs.getString("text"), instant(rs.getObject("updated_at", OffsetDateTime.class))))
			.list();
		Map<UUID, ArtifactView> readable = artifacts
			.readableAmong(candidates.stream().map(Candidate::artifactId).collect(Collectors.toSet())).stream()
			.collect(Collectors.toMap(ArtifactView::id, Function.identity()));
		return candidates.stream()
			.filter(c -> readable.containsKey(c.artifactId()))
			.limit(limit)
			.map(c -> new DocumentHit(readable.get(c.artifactId()), c.type(), c.status(), c.text(), c.updatedAt()))
			.toList();
	}

	private static Instant instant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	private record Candidate(UUID artifactId, String type, String status, String text, Instant updatedAt) {
	}

	/** Página encontrada: {@code type} é {@code PAGE} ou {@code REGIMENTO}; {@code text}, o texto da versão lida. */
	public record PageHit(UUID id, UUID orgUnitId, String type, String title, String text, Instant updatedAt) {
	}

	/** Documento encontrado: o artefato dono, o tipo (PRD, SAD...), o status e o texto da versão atual. */
	public record DocumentHit(ArtifactView artifact, String type, String status, String text, Instant updatedAt) {
	}
}
