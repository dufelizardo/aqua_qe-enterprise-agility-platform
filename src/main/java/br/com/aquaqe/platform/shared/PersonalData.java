package br.com.aquaqe.platform.shared;

import java.util.UUID;

/**
 * Dados pessoais que um módulo guarda nas próprias tabelas (#32; REV-14; SSD 2.2). Cada módulo que guarda algo de uma
 * pessoa implementa esta interface; o módulo {@code privacy} junta as respostas para exportar e anonimizar, sem tocar nas
 * tabelas dos outros módulos (ADR-05).
 */
public interface PersonalData {

	/** Nome da seção na exportação (por exemplo, {@code "identity"}). */
	String section();

	/** O que o módulo guarda da pessoa, pronto para virar JSON. Lista ou mapa vazio quando não há nada. */
	Object export(UUID userId);

	/**
	 * Apaga ou pseudonimiza o que o módulo guarda da pessoa, na transação corrente. O id da pessoa continua nas
	 * referências (trilha, autoria, responsáveis): sem o nome, ele não identifica mais ninguém.
	 */
	void anonymize(UUID userId);

	/** Ordem de execução na anonimização; a conta ({@code identity}) vai por último. */
	default int order() {
		return 0;
	}
}
