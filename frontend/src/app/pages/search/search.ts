import { Component, input } from '@angular/core';

/**
 * Resultados de busca (UX/UI, seção 7.9). A API de busca (GET /search, ADR-11) ainda não existe; a tela só confirma o
 * termo pesquisado.
 */
@Component({
  selector: 'app-search',
  template: `
    <h1 data-testid="page-title">Busca</h1>
    @if (q()) {
      <p data-testid="search-term">Termo pesquisado: <strong>{{ q() }}</strong></p>
    }
    <p data-testid="search-unavailable">A busca de artefatos e documentos será entregue junto com o módulo de busca.</p>
  `,
  styles: `
    h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 4px; color: var(--text); }
    p { color: var(--text-secondary); margin: 0 0 4px; }
  `,
})
export class Search {
  readonly q = input('');
}
