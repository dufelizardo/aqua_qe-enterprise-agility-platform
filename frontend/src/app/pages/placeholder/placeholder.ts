import { Component, input } from '@angular/core';

/** Tela ainda não implementada; recebe nome e Flight Level pelos dados da rota. */
@Component({
  selector: 'app-placeholder',
  template: `
    <h1 data-testid="page-title">{{ screen() }}</h1>
    <p data-testid="placeholder">Esta tela ({{ level() }}) será entregue junto com o módulo correspondente.</p>
  `,
  styles: `
    h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 4px; color: var(--text); }
    p { color: var(--text-secondary); margin: 0; }
  `,
})
export class Placeholder {
  readonly screen = input('');
  readonly level = input('');
}
