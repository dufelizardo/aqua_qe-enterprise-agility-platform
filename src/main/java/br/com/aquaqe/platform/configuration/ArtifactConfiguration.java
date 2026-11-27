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

	/**
	 * Campos customizados declarados no tipo, na ordem (#17; REV-12). Com {@code typeKey} nulo, os de todos os tipos. Só
	 * os declarados podem ser gravados no artefato e citados num gate.
	 */
	List<CustomField> customFields(String typeKey);

	/**
	 * Primeiro status do workflow vigente do tipo na unidade; todo artefato nasce nele (SSD 3.4). O vigente é o ativo da
	 * unidade mais funda da linhagem que tenha um para o tipo (#49); a raiz sempre tem.
	 */
	Status initialStatus(String typeKey, UUID orgUnitId);

	/** O workflow vigente do tipo na unidade (#49). */
	Optional<UUID> effectiveWorkflowId(UUID orgUnitId, String typeKey);

	/** A versão de workflow, com tipos e status; vazio se não existe (migração explícita, #49). */
	Optional<WorkflowScope> workflowScope(UUID workflowId);

	Map<UUID, Status> statuses(Collection<UUID> ids);

	/** Ids dos status com a chave e/ou a categoria informadas (filtros de listagem); {@code null} ignora o critério. */
	Set<UUID> statusIds(String key, String category);

	/**
	 * Status em sequência dos workflows ativos de um nível que valem para a unidade ou para as de baixo (os da linhagem
	 * e os das unidades da subárvore, #49), sem repetir a chave e sem os de fora da sequência (Cancelado), na ordem da
	 * posição. É a base das colunas dos quadros da unidade (SSD 3.9). {@code id} vem nulo, porque a mesma chave pode
	 * existir em mais de um workflow.
	 */
	List<Status> sequenceOfLevel(String level, UUID orgUnitId);

	/** Transições do workflow que saem do status informado (SSD 8.2). */
	List<StatusTransition> transitionsFrom(UUID statusId);

	/** A transição, com o workflow e os tipos de artefato dele; vazio se não existe (gates, SSD 5). */
	Optional<TransitionScope> transition(UUID transitionId);

	/** Transição com o workflow a que pertence e os tipos de artefato que o usam. */
	record TransitionScope(StatusTransition transition, UUID workflowId, String workflowName, List<String> artifactTypes) {
	}

	record StatusTransition(UUID id, Status from, Status to) {
	}

	/** Uma versão de workflow: {@code active} só na mais recente da família. */
	record WorkflowScope(UUID id, UUID familyId, UUID orgUnitId, boolean active, int versionNumber, String name,
			List<String> artifactTypes, List<Status> statuses) {
	}

	record ArtifactType(UUID id, String key, String level, String name, String keyPrefix, boolean parentRequired,
			Set<String> parentTypes) {
	}

	/**
	 * Campo customizado de um tipo de artefato. {@code type}: {@code TEXT}, {@code NUMBER}, {@code DATE} (AAAA-MM-DD),
	 * {@code BOOLEAN} ou {@code SELECT} (um dos {@code options}).
	 */
	record CustomField(UUID id, String artifactType, String key, String label, String type, List<String> options,
			int position) {
	}

	record Status(UUID id, String key, String name, String phase, String category, Integer position) {
	}
}
