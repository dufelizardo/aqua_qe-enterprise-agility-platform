import { Component, inject } from '@angular/core';

import { SessionService } from '../../core/session.service';

/**
 * Home — Meu trabalho (UX/UI, seção 7.2). A área de conteúdo é uma grade de cartões, preparada para receber widgets;
 * os blocos de aprovações e itens entram com os módulos correspondentes.
 */
@Component({
  selector: 'app-home',
  template: `
    <h1 data-testid="page-title">Meu trabalho</h1>
    <p data-testid="welcome">Olá, {{ session.displayName() }}.</p>
    <section class="widgets" aria-label="Meu trabalho">
      <article class="card" data-testid="empty-state">
        <p>Nada por aqui ainda. As aprovações pendentes e os itens sob sua responsabilidade aparecerão nesta página.</p>
      </article>
    </section>
  `,
  styles: `
    h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 4px; color: var(--text); }
    p { color: var(--text-secondary); margin: 0; }
    .widgets { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 320px), 1fr)); gap: 16px; margin-top: 24px; }
    .card { grid-column: 1 / -1; padding: 24px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
  `,
})
export class Home {
  protected readonly session = inject(SessionService);
}
