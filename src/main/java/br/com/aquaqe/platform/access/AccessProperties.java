package br.com.aquaqe.platform.access;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Papéis iniciais, dados na unidade raiz quando a conta é criada (SSD, seção 8.5).
 *
 * @param adminUsername usuário do IdP que recebe {@code PLATFORM_ADMIN} na raiz no primeiro acesso ("usuário inicial
 *        indicado na instalação")
 * @param personaRoles dá a cada persona de teste ({@code aquaqe.auth.dev-personas}) o seu papel na raiz; ligado
 *        automaticamente no modo sem senha e proibido em produção
 */
@ConfigurationProperties("aquaqe.access")
record AccessProperties(String adminUsername, @DefaultValue("false") boolean personaRoles) {
}
