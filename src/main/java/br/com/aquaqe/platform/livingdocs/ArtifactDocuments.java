package br.com.aquaqe.platform.livingdocs;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.aquaqe.platform.configuration.DocumentTemplates;
import br.com.aquaqe.platform.configuration.DocumentTemplates.Section;

/**
 * API pública do módulo {@code livingdocs} para as regras de gate (SSD 5.2; #78): o status de um documento do artefato
 * e as seções preenchidas na versão atual. Não confere leitura: quem chama já avaliou o artefato.
 */
@Service
public class ArtifactDocuments {

	private final DocumentRepository repository;
	private final DocumentSections sections;
	private final DocumentTemplates templates;

	ArtifactDocuments(DocumentRepository repository, DocumentSections sections, DocumentTemplates templates) {
		this.repository = repository;
		this.sections = sections;
		this.templates = templates;
	}

	/** Status ({@code PENDING}, {@code IN_PROGRESS}, {@code APPROVED}) e seções preenchidas; vazio se não há o documento. */
	public Optional<DocumentState> state(UUID artifactId, String type) {
		return repository.documentOf(artifactId, type).map(doc -> {
			Set<String> filled = new LinkedHashSet<>();
			if (doc.currentVersion() > 0) {
				repository.version(doc.id(), doc.currentVersion()).ifPresent(version -> {
					for (Map.Entry<String, List<?>> section : sections.blocksByKey(version.sectionsJson()).entrySet()) {
						if (Blocks.filled(section.getValue())) {
							filled.add(section.getKey());
						}
					}
				});
			}
			return new DocumentState(doc.type(), doc.status(), filled);
		});
	}

	/** Chaves das seções do template atual do tipo de documento (PRD, SAD...), para validar as regras ao salvar o gate. */
	public List<String> sectionKeys(String type) {
		return templates.latest(type).map(t -> t.sections().stream().map(Section::key).toList()).orElse(List.of());
	}

	/** Documento do artefato: tipo, status e as chaves das seções preenchidas na versão atual. */
	public record DocumentState(String type, String status, Set<String> filledSections) {
	}
}
