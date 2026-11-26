package br.com.aquaqe.platform.governance;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import br.com.aquaqe.platform.configuration.ArtifactConfiguration.TransitionScope;
import br.com.aquaqe.platform.workitem.ArtifactViews.ArtifactView;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Avaliador de um tipo de regra de gate (SSD 5.2, "Extensão"). O {@code governance} traz os de campo, documento,
 * filhos e aprovação; novos tipos (business case, cobertura de testes) entram registrando outro avaliador.
 */
public interface GateRuleEvaluator {

	/** O tipo de regra, como {@code REQUIRED_FIELD}. */
	String type();

	/** {@code VALIDATION}, avaliada pelo sistema, ou {@code APPROVAL}, que depende de uma pessoa. */
	default String category() {
		return "VALIDATION";
	}

	/**
	 * Confere os parâmetros ao salvar o gate e devolve a forma gravada.
	 *
	 * @param field prefixo do campo nos erros ({@code rules[0].params})
	 * @throws br.com.aquaqe.platform.shared.ApiException {@code VALIDATION_FAILED} com o campo que falhou
	 */
	Map<String, Object> validate(Map<String, Object> params, TransitionScope scope, String field);

	/** A regra está atendida para o artefato? */
	boolean satisfied(Map<String, Object> params, Context context);

	/** O que o avaliador precisa saber do artefato. A visão completa só é montada se algum avaliador pedir. */
	record Context(ArtifactRef artifact, Supplier<ArtifactView> view, UUID ruleId) {
	}
}
