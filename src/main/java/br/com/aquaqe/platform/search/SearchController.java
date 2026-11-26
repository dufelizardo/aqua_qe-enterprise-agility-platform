package br.com.aquaqe.platform.search;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.aquaqe.platform.livingdocs.DocumentSearch;
import br.com.aquaqe.platform.organization.OrgUnits;
import br.com.aquaqe.platform.organization.OrgUnits.OrgUnitSummary;
import br.com.aquaqe.platform.shared.ApiException;
import br.com.aquaqe.platform.shared.TextSearch;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.Artifacts;

/**
 * {@code GET /search?q=&kind=} (SSD 3.10; #84): artefatos, documentos do artefato e páginas do Wiki que a pessoa lê,
 * com todos os termos, sem diferença de maiúsculas e acentos. Até {@value #LIMIT} resultados de cada tipo; {@code kind}
 * ({@code ARTIFACT}, {@code DOCUMENT} ou {@code PAGE}) limita a um tipo.
 */
@RestController
@RequestMapping("/api/v1/search")
class SearchController {

	static final int LIMIT = 20;
	static final List<String> KINDS = List.of("ARTIFACT", "DOCUMENT", "PAGE");

	private final Artifacts artifacts;
	private final DocumentSearch documents;
	private final OrgUnits orgUnits;

	SearchController(Artifacts artifacts, DocumentSearch documents, OrgUnits orgUnits) {
		this.artifacts = artifacts;
		this.documents = documents;
		this.orgUnits = orgUnits;
	}

	@GetMapping
	Results search(@RequestParam(required = false) String q, @RequestParam(required = false) String kind) {
		List<String> terms = TextSearch.terms(q);
		if (kind != null && !KINDS.contains(kind)) {
			throw ApiException.validation("kind", "use ARTIFACT, DOCUMENT ou PAGE");
		}
		Map<UUID, Unit> units = new HashMap<>();
		List<Hit> artifactHits = wants(kind, "ARTIFACT") ? artifacts.search(terms, LIMIT).stream()
			.map(a -> artifact(a, terms)).toList() : List.of();
		List<Hit> documentHits = wants(kind, "DOCUMENT") ? documents.artifactDocuments(terms, LIMIT).stream()
			.map(d -> new Hit("DOCUMENT", d.artifact().title(), d.artifact().key(), d.type(), null, null, d.status(),
				unit(d.artifact().orgUnit().id(), d.artifact().orgUnit().name()), TextSearch.snippet(d.text(), terms),
				d.updatedAt()))
			.toList() : List.of();
		List<Hit> pageHits = wants(kind, "PAGE") ? documents.pages(terms, LIMIT).stream()
			.map(p -> new Hit("PAGE", p.title(), null, null, p.id(), p.type(), null, unit(p.orgUnitId(), units),
				TextSearch.snippet(p.text(), terms), p.updatedAt()))
			.toList() : List.of();
		return new Results(q.strip(), artifactHits, documentHits, pageHits);
	}

	private Hit artifact(ArtifactView a, List<String> terms) {
		return new Hit("ARTIFACT", a.title(), a.key(), null, null, a.type(), a.status() == null ? null : a.status().name(),
			unit(a.orgUnit().id(), a.orgUnit().name()), TextSearch.snippet(a.description(), terms), a.updatedAt());
	}

	private static boolean wants(String kind, String value) {
		return kind == null || kind.equals(value);
	}

	private static Unit unit(UUID id, String name) {
		return new Unit(id, name);
	}

	private Unit unit(UUID id, Map<UUID, Unit> cache) {
		return cache.computeIfAbsent(id, key -> new Unit(key, orgUnits.find(key).map(OrgUnitSummary::name).orElse(null)));
	}

	record Unit(UUID id, String name) {
	}

	/**
	 * Um resultado. Artefato: {@code key}, {@code type} (tipo do artefato) e {@code status}. Documento: {@code key} do
	 * artefato, {@code documentType} e {@code status} do documento; {@code title} é o do artefato. Página: {@code pageId}
	 * e {@code type} ({@code PAGE} ou {@code REGIMENTO}). {@code snippet}: trecho do texto em volta do termo.
	 */
	record Hit(String kind, String title, String key, String documentType, UUID pageId, String type, String status,
			Unit orgUnit, String snippet, Instant updatedAt) {
	}

	record Results(String q, List<Hit> artifacts, List<Hit> documents, List<Hit> pages) {
	}
}
