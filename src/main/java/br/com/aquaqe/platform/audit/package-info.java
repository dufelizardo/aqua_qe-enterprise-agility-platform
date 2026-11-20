/**
 * Módulo {@code audit} — trilha de auditoria append-only com cadeia de hash.
 * <p>
 * Ver doc/SAD.md, seção 5. Outros módulos só acessam este pela API pública (este pacote) ou por eventos (ADR-04).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Audit")
package br.com.aquaqe.platform.audit;
