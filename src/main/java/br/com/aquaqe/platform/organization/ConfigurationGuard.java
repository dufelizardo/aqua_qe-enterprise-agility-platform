package br.com.aquaqe.platform.organization;

import java.util.UUID;

/**
 * Verifica se o usuário corrente pode gerir a configuração de uma unidade (SSD, seção 2: "Gerir configuração e papéis").
 * <p>
 * Implementada pelo módulo {@code access}, que depende deste. Assim {@code organization} não depende de {@code access}
 * (mesmo padrão do {@code TransitionGuard}, SAD 5.1).
 */
public interface ConfigurationGuard {

	/**
	 * @return o id da conta do usuário que age
	 * @throws br.com.aquaqe.platform.shared.ApiException {@code FORBIDDEN} se o usuário não pode gerir a unidade
	 */
	UUID requireManage(UUID orgUnitId);
}
