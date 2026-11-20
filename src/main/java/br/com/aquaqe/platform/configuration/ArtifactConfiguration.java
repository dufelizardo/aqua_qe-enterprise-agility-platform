package br.com.aquaqe.platform.configuration;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** API pública do módulo {@code configuration}: tipos de artefato e status dos workflows (SSD 3.3, 8.1 e 8.2). */
public interface ArtifactConfiguration {

	List<ArtifactType> types();

	Optional<ArtifactType> type(String key);

	Optional<ArtifactType> type(UUID id);

	/** Primeiro status do workflow ativo do tipo; todo artefato nasce nele (SSD 3.4). */
	Status initialStatus(String typeKey);

	Map<UUID, Status> statuses(Collection<UUID> ids);

	/** Ids dos status com a chave e/ou a categoria informadas (filtros de listagem); {@code null} ignora o critério. */
	Set<UUID> statusIds(String key, String category);

	record ArtifactType(UUID id, String key, String level, String name, String keyPrefix, boolean parentRequired,
			Set<String> parentTypes) {
	}

	record Status(UUID id, String key, String name, String phase, String category, Integer position) {
	}
}
