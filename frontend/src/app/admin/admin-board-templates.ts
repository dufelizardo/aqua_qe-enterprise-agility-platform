import { Component, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { messageOf, toApiError } from '../core/api-error';
import { BoardTemplate, BoardTemplatesService, columnsSummary } from '../work/board-templates.service';

const LEVEL_NAMES: Record<string, string> = { FL3: 'Portfólio (FL3)', FL2: 'Tático (FL2)', FL1: 'Squad (FL1)' };

/**
 * Administração › Templates de quadro (#50; MDL-07): o catálogo único, por nível. O administrador renomeia e remove;
 * os templates nascem de "Salvar como template" na configuração de um quadro. Remover não altera os quadros criados.
 */
@Component({
  selector: 'app-admin-board-templates',
  imports: [MatSnackBarModule, RouterLink],
  template: `
    <header class="page-header">
      <p class="eyebrow">Administração</p>
      <nav class="admin-tabs" aria-label="Seções da administração">
        <a routerLink="/admin/organizacao">Estrutura organizacional</a>
        <a routerLink="/admin/workflows">Workflows</a>
        <a routerLink="/admin/gates">Gates</a>
        <a routerLink="/admin/campos-customizados">Campos customizados</a>
        <a routerLink="/admin/responsavel-padrao">Responsável padrão</a>
        <a routerLink="/admin/templates-de-quadro" class="on" aria-current="page">Templates de quadro</a>
      </nav>
      <h1 data-testid="page-title">Templates de quadro</h1>
      <p class="lead">
        Modelos de colunas para criar quadros. Quem gere um quadro salva as colunas dele como template, na configuração do
        quadro. Cada quadro recebe uma cópia: mudar ou remover um template não altera os quadros existentes.
      </p>
    </header>

    @if (loadError(); as e) {
      <p class="card error" role="alert">{{ e }}</p>
    }

    @for (group of groups(); track group.level) {
      <section class="card" [attr.aria-label]="group.name">
        <h2>{{ group.name }}</h2>
        <ul class="templates">
          @for (t of group.items; track t.id) {
            <li class="template" data-testid="bt-row" [attr.data-name]="t.name" [attr.data-level]="t.level">
              @if (editingId() === t.id) {
                <div class="edit">
                  <input [value]="editName()" (input)="editName.set($any($event.target).value)" maxlength="120"
                         aria-label="Nome do template" data-testid="bt-edit-name" />
                  <input [value]="editDescription()" (input)="editDescription.set($any($event.target).value)" maxlength="300"
                         aria-label="Descrição" placeholder="Descrição (opcional)" />
                  <div class="actions">
                    <button type="button" class="btn primary" [disabled]="busy() || !editName().trim()" (click)="save(t)"
                            data-testid="bt-save">Salvar</button>
                    <button type="button" class="btn" (click)="editingId.set(null)">Cancelar</button>
                  </div>
                </div>
              } @else {
                <div>
                  <strong data-testid="bt-name">{{ t.name }}</strong>
                  @if (t.builtin) {
                    <span class="badge">Da instalação</span>
                  }
                  @if (t.description) {
                    <p class="muted">{{ t.description }}</p>
                  }
                  <p class="columns">{{ summary(t) }}</p>
                </div>
                <div class="actions">
                  <button type="button" class="btn" (click)="edit(t)" data-testid="bt-edit">Renomear</button>
                  <button type="button" class="btn" (click)="removingId.set(t.id)" data-testid="bt-remove">Remover</button>
                </div>
              }
              @if (removingId() === t.id) {
                <div class="notice" role="alertdialog" data-testid="bt-remove-confirm">
                  <p><strong>Remover "{{ t.name }}"?</strong> Os quadros já criados com ele não mudam.</p>
                  <button type="button" class="btn danger" [disabled]="busy()" (click)="remove(t)" data-testid="bt-remove-ok">Remover</button>
                  <button type="button" class="btn" (click)="removingId.set(null)">Cancelar</button>
                </div>
              }
            </li>
          } @empty {
            <li class="muted">Nenhum template neste nível.</li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    .page-header { margin-bottom: 16px; }
    .eyebrow { margin: 0; font-size: 12px; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); }
    h1 { margin: 4px 0; font-size: 30px; font-weight: 800; }
    h2 { margin: 0 0 8px; font-size: 16px; }
    .lead { margin: 0; color: var(--text-secondary); max-width: 760px; }
    .admin-tabs { display: flex; flex-wrap: wrap; gap: 12px; margin: 6px 0; }
    .admin-tabs a { color: var(--text-secondary); text-decoration: none; font-weight: 600; }
    .admin-tabs a.on { color: var(--text); border-bottom: 2px solid var(--navy-900, currentColor); }
    .card { padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); margin-bottom: 12px; }
    .templates { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
    .template { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 10px; }
    .template p { margin: 2px 0 0; }
    .columns { font-size: 13px; }
    .badge { margin-left: 6px; padding: 1px 6px; border-radius: 999px; font-size: 11px; background: var(--surface-muted, #eef1f6); color: var(--text-secondary); }
    .actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: flex-start; }
    .edit { display: grid; gap: 6px; flex: 1; max-width: 520px; }
    input { min-height: 32px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; background: var(--surface); color: var(--text); }
    .notice { flex-basis: 100%; padding: 10px 12px; border-radius: 8px; background: #fff7e6; color: var(--text); }
    .notice p { margin: 0 0 8px; }
    .notice .btn + .btn { margin-left: 6px; }
    .muted { color: var(--text-secondary); font-size: 13px; }
    .error { color: var(--danger, #b42318); }
  `,
})
export class AdminBoardTemplates {
  private readonly api = inject(BoardTemplatesService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly templates = signal<BoardTemplate[]>([]);
  protected readonly groups = computed(() =>
    ['FL3', 'FL2', 'FL1'].map((level) => ({
      level,
      name: LEVEL_NAMES[level],
      items: this.templates().filter((t) => t.level === level),
    })),
  );
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected readonly editName = signal('');
  protected readonly editDescription = signal('');
  protected readonly removingId = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  protected summary(t: BoardTemplate): string {
    return columnsSummary(t);
  }

  protected edit(t: BoardTemplate): void {
    this.removingId.set(null);
    this.editingId.set(t.id);
    this.editName.set(t.name);
    this.editDescription.set(t.description ?? '');
  }

  protected async save(t: BoardTemplate): Promise<void> {
    await this.run(async () => {
      await this.api.update(t.id, this.editName(), this.editDescription());
      this.editingId.set(null);
      await this.load();
      this.toast('Template atualizado.');
    });
  }

  protected async remove(t: BoardTemplate): Promise<void> {
    await this.run(async () => {
      await this.api.remove(t.id);
      this.removingId.set(null);
      await this.load();
      this.toast('Template removido. Os quadros criados com ele não mudam.');
    });
  }

  private async load(): Promise<void> {
    try {
      this.templates.set(await this.api.list());
    } catch (error) {
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      const api = toApiError(error);
      this.toast(api.errors[0]?.message ?? messageOf(api), 5000);
    } finally {
      this.busy.set(false);
    }
  }

  private toast(message: string, duration = 3000): void {
    this.snackBar.open(message, 'Fechar', { duration });
  }
}
