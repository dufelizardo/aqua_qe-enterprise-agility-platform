package br.com.aquaqe.platform.workitem;

import java.util.UUID;

/**
 * Evento em memória publicado na mesma transação em que o artefato é criado. O módulo {@code livingdocs} o escuta para
 * criar os documentos da Iniciativa e da Feature (SSD 3.7) sem que {@code workitem} conheça documentos.
 */
public record ArtifactCreated(UUID artifactId, String key, String artifactType, UUID orgUnitId, UUID actorId) {
}
