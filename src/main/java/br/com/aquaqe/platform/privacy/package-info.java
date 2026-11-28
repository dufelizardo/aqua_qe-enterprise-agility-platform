/**
 * Módulo {@code privacy} — dados pessoais (#32; REV-14; SSD 2.2): o diretório de pessoas da Administração, a exportação
 * dos dados de uma pessoa e a anonimização da conta. Não tem tabelas: junta o que cada módulo devolve pela interface
 * {@link br.com.aquaqe.platform.shared.PersonalData}.
 * <p>
 * O produto oferece as capacidades; quem opera a instalação responde pela conformidade (PRD, DEC-05).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Privacy")
package br.com.aquaqe.platform.privacy;
