/**
 * Módulo {@code events} — outbox transacional e entrega de eventos de domínio (ADR-06).
 * <p>
 * Ver doc/SAD.md, seção 5. Outros módulos só acessam este pela API pública (este pacote) ou por eventos (ADR-04).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Events")
package br.com.aquaqe.platform.events;
