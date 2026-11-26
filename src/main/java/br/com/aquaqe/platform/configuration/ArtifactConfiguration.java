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

	/**
	 * Status em sequência dos workflows ativos de um nível, sem repetir a chave e sem os de fora da sequência
	 * (Cancelado), na ordem da posição. É a base das colunas padrão dos quadros (SSD 3.9). {@code id} vem nulo, porque
	 * a mesma chave pode existir em mais de um workflow.
	 */
	List<Status> sequenceOfLevel(String level);

	/** Transições do workflow que saem do status informado (SSD 8.2). */
	List<StatusTransition> transitionsFrom(UUID statusId);

	/** A transição, com o workflow e os tipos de artefato dele; vazio se não existe (gates, SSD 5). */
	Optional<TransitionScope> transition(UUID transitionId);

	/** Transição com o workflow a que pertence e os tipos de artefato que o usam. */
	record TransitionScope(StatusTransition transition, UUID workflowId, String workflowName, List<String> artifactTypes) {
	}

	record StatusTransition(UUID id, Status from, Status to) {
	}

	record ArtifactType(UUID id, String key, String level, String name, String keyPrefix, boolean parentRequired,
			Set<String> parentTypes) {
	}

	record Status(UUID id, String key, String name, String phase, String category, Integer position) {
	}
}
