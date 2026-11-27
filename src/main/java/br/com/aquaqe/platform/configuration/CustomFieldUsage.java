package br.com.aquaqe.platform.configuration;

import java.util.List;

/**
 * Quem usa um campo customizado (#17). O {@code governance} implementa para os gates; remover um campo em uso é recusado
 * com a lista devolvida, para não deixar regra citando campo que não existe mais.
 */
public interface CustomFieldUsage {

	/** Descrições legíveis dos usos do campo no tipo, como {@code "Gate Pronto para validar (Workflow FL1)"}. */
	List<String> usages(String artifactType, String fieldKey);
}
