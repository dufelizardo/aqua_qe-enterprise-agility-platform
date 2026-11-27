package br.com.aquaqe.platform.configuration;

import java.util.Map;
import java.util.UUID;

/**
 * Uma versão de workflow foi publicada (#49): nova versão da família ou cópia para uma unidade. {@code transitions} liga
 * cada transição da versão de origem à transição equivalente (mesmos status de saída e de chegada, pela chave) da nova.
 * O {@code governance} copia os gates por ela, na mesma transação.
 */
public record WorkflowVersionPublished(UUID previousWorkflowId, UUID workflowId, Map<UUID, UUID> transitions,
		UUID actorId) {
}
