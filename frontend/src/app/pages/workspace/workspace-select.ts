import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';

import { WorkspaceService } from '../../core/workspace.service';

/**
 * Seleção de workspace (UX/UI 7.1): as unidades em que o usuário tem papel, com os papéis em cada uma. Com uma única
 * unidade a tela é pulada (o serviço escolhe sozinho).
 */
@Component({
  selector: 'app-workspace-select',
  template: `
    <h1 data-testid="page-title">Escolha o workspace</h1>
    <p class="lead">A unidade em que você vai trabalhar. Dá para trocar depois, pelo cabeçalho.</p>
    @if (workspace.workspaces().length === 0) {
      <p class="card" data-testid="workspace-empty">
        Você ainda não tem acesso a nenhuma unidade. Peça ao administrador da plataforma.
      </p>
    } @else {
      <ul class="list" data-testid="workspace-list">
        @for (item of workspace.workspaces(); track item.id) {
          <li>
            <button type="button" class="card option" (click)="choose(item.id)" data-testid="workspace-option">
              <strong>{{ item.name }}</strong>
              <span class="muted">{{ item.roles.join(' · ') }}</span>
            </button>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 4px; color: var(--text); }
    .lead { color: var(--text-secondary); margin: 0 0 24px; }
    .list { display: grid; gap: 12px; max-width: 560px; margin: 0; padding: 0; list-style: none; }
    .card { padding: 16px 20px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
    .option {
      display: flex; flex-direction: column; gap: 4px; width: 100%; font: inherit; text-align: left; color: var(--text);
      cursor: pointer;
    }
    .option:hover { border-color: var(--blue-700); }
    .option:focus-visible { outline: 2px solid var(--blue-500); outline-offset: 2px; }
    .muted { color: var(--text-muted); font-size: 13px; }
  `,
})
export class WorkspaceSelect {
  protected readonly workspace = inject(WorkspaceService);
  private readonly router = inject(Router);
  /** Para onde voltar depois de escolher (query param). */
  readonly voltar = input<string>();

  constructor() {
    if (!this.workspace.loaded()) {
      void this.workspace.load();
    }
  }

  protected choose(id: string): void {
    this.workspace.choose(id);
    void this.router.navigateByUrl(this.voltar() || '/home');
  }
}
