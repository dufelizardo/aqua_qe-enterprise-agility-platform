/**
 * Módulo {@code notification} — avisos para cada pessoa: menção e resposta em comentários de páginas (#85). Cria os
 * avisos a partir dos eventos da outbox, sem depender dos módulos que os publicam.
 * <p>
 * Ver doc/SAD.md, seção 5. Outros módulos só acessam este pela API pública (este pacote) ou por eventos (ADR-04).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package br.com.aquaqe.platform.notification;
