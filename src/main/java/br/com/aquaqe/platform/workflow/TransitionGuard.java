package br.com.aquaqe.platform.workflow;

import java.util.List;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.StatusTransition;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Guarda de transição (SAD 5.1 e 7.3). O módulo {@code workflow} chama todas as implementações antes de mudar o status;
 * o módulo {@code governance} a implementa com as regras do gate (#78). Assim {@code workflow} não conhece gates nem
 * documentos.
 */
public interface TransitionGuard {

	/** Regras não atendidas para executar a transição; lista vazia libera. */
	List<FailedRule> evaluate(ArtifactRef artifact, StatusTransition transition);

	/**
	 * Regra não atendida, no formato de {@code failedRules} da resposta {@code GATE_BLOCKED} (SSD 5.3): {@code category}
	 * é {@code VALIDATION} ou {@code APPROVAL}; {@code params} leva a interface ao que falta; {@code gateName} é o gate.
	 */
	record FailedRule(String ruleId, String type, String category, String message, java.util.Map<String, Object> params,
			String gateName) {

		/** Regra de validação sem parâmetros nem gate (como o limite de WIP dos quadros). */
		public FailedRule(String ruleId, String type, String message) {
			this(ruleId, type, "VALIDATION", message, java.util.Map.of(), null);
		}
	}
}
