/**
 * Módulo {@code search} — busca textual em artefatos, documentos do artefato e páginas do Wiki (ADR-11; #84). Não tem
 * tabelas: compõe as buscas que {@code workitem} e {@code livingdocs} expõem, cada uma com as próprias regras de leitura.
 * <p>
 * Ver doc/SAD.md, seção 5. Outros módulos só acessam este pela API pública (este pacote) ou por eventos (ADR-04).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Search")
package br.com.aquaqe.platform.search;
