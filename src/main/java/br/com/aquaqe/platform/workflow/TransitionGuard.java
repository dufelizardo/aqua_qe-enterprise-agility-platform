package br.com.aquaqe.platform.workflow;

import java.util.List;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.StatusTransition;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Guarda de transição (SAD 5.1 e 7.3). O módulo {@code workflow} chama todas as implementações antes de mudar o status;
 * o módulo {@code governance} vai implementá-la com as regras do gate. Assim {@code workflow} não conhece gates nem
 * documentos.
 */
public interface TransitionGuard {

	/** Regras não atendidas para executar a transição; lista vazia libera. */
	List<FailedRule> evaluate(ArtifactRef artifact, StatusTransition transition);

	/** Regra não atendida, no formato de {@code failedRules} da resposta {@code GATE_BLOCKED} (SSD 5.3). */
	record FailedRule(String ruleId, String type, String message) {
	}
}
