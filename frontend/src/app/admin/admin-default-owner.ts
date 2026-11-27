import { Component, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { messageOf, toApiError } from '../core/api-error';
import { ROLE_LABELS } from '../work/gates.service';
import {
  DefaultOwnerService,
  OWNER_MODE_LABELS,
  OWNER_ROLES,
  OwnerMode,
  OwnerRule,
  ruleSummary,
} from '../work/default-owner.service';
import { OrganizationService, TreeRow, buildTree } from './organization.service';

/**
 * Administração › Responsável padrão (#43; SSD 3.4): para cada unidade e tipo de artefato, quem fica como Owner do item
 * criado sem um. A unidade sem regra própria herda a de cima; sem regra na linhagem, o item nasce sem Owner.
 */
@Component({
  selector: 'app-admin-default-owner',
  imports: [MatSnackBarModule, RouterLink],
  template: `
    <header class="page-header">
      <p class="eyebrow">Administração</p>
      <nav class="admin-tabs" aria-label="Seções da administração">
        <a routerLink="/admin/organizacao">Estrutura organizacional</a>
        <a routerLink="/admin/workflows">Workflows</a>
        <a routerLink="/admin/gates">Gates</a>
        <a routerLink="/admin/campos-customizados">Campos customizados</a>
        <a routerLink="/admin/responsavel-padrao" class="on" aria-current="page">Responsável padrão</a>
        <a routerLink="/admin/templates-de-quadro">Templates de quadro</a>
      </nav>
      <h1 data-testid="page-title">Responsável padrão</h1>
      <p class="lead">
        Quem fica como Owner de um item criado sem responsável. A unidade sem regra própria segue a da unidade acima.
      </p>
    </header>

    @if (loadError(); as e) {
      <p class="card error" role="alert">{{ e }}</p>
    }

    <section class="card">
      <label class="field">
        Unidade
        <select [value]="unitId() ?? ''" (change)="chooseUnit($any($event.target).value)" data-testid="owner-unit">
          @for (row of tree(); track row.unit.id) {
            <option [value]="row.unit.id" [selected]="row.unit.id === unitId()">
              {{ indent(row.depth) }}{{ row.unit.name }} ({{ row.unit.level.name }})
            </option>
          }
        </select>
      </label>

      <table class="rules" aria-label="Regras por tipo de artefato">
        <thead>
          <tr><th scope="col">Tipo</th><th scope="col">Regra</th><th scope="col">Origem</th><th scope="col"><span class="sr-only">Ações</span></th></tr>
        </thead>
        <tbody>
          @for (rule of rules(); track rule.artifactType.key) {
            <tr [attr.data-testid]="'owner-row-' + rule.artifactType.key">
              <td>{{ rule.artifactType.name }} <span class="muted">{{ rule.artifactType.level }}</span></td>
              @if (editing() === rule.artifactType.key) {
                <td colspan="2">
                  <div class="row">
                    <select [value]="mode()" (change)="mode.set($any($event.target).value)" aria-label="Regra" data-testid="owner-mode">
                      @for (m of modes; track m) {
                        <option [value]="m" [selected]="m === mode()">{{ modeLabels[m] }}</option>
                      }
                    </select>
                    @if (mode() === 'ROLE') {
                      <select [value]="role()" (change)="role.set($any($event.target).value)" aria-label="Papel" data-testid="owner-role">
                        @for (r of roles; track r) {
                          <option [value]="r" [selected]="r === role()">{{ roleLabels[r] }}</option>
                        }
                      </select>
                    }
                  </div>
                  @if (mode() === 'ROLE') {
                    <p class="hint">
                      Vale quem tem o papel na unidade do item ou, se ninguém lá tiver, na unidade acima mais próxima. Com mais de
                      uma pessoa, quem cria o item escolhe.
                    </p>
                  }
                </td>
                <td class="actions">
                  <button type="button" class="btn primary" [disabled]="busy()" (click)="save(rule)" data-testid="owner-save">Salvar</button>
                  <button type="button" class="btn" (click)="editing.set(null)">Cancelar</button>
                </td>
              } @else {
                <td [attr.data-testid]="'owner-summary-' + rule.artifactType.key">{{ summary(rule) }}</td>
                <td class="muted">
                  @if (!rule.inherited) {
                    Desta unidade
                  } @else if (rule.source; as s) {
                    Herdada de {{ s.name }}
                  } @else {
                    Padrão
                  }
                </td>
                <td class="actions">
                  <button type="button" class="btn" (click)="edit(rule)" [attr.data-testid]="'owner-edit-' + rule.artifactType.key">Alterar</button>
                  @if (!rule.inherited) {
                    <button type="button" class="btn" [disabled]="busy()" (click)="inherit(rule)" [attr.data-testid]="'owner-inherit-' + rule.artifactType.key">
                      Seguir a de cima
                    </button>
                  }
                </td>
              }
            </tr>
          }
        </tbody>
      </table>
    </section>
  `,
  styles: `
    .page-header { margin-bottom: 16px; }
    .eyebrow { margin: 0; font-size: 12px; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); }
    h1 { margin: 4px 0; font-size: 30px; font-weight: 800; }
    .lead { margin: 0; color: var(--text-secondary); }
    .admin-tabs { display: flex; flex-wrap: wrap; gap: 12px; margin: 6px 0; }
    .admin-tabs a { color: var(--text-secondary); text-decoration: none; font-weight: 600; }
    .admin-tabs a.on { color: var(--text); border-bottom: 2px solid var(--navy-900, currentColor); }
    .card { padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
    .field { display: grid; gap: 4px; font-size: 13px; font-weight: 600; margin-bottom: 12px; max-width: 480px; }
    select { min-height: 32px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; background: var(--surface); color: var(--text); }
    .rules { width: 100%; border-collapse: collapse; }
    .rules th { text-align: left; font-size: 12px; color: var(--text-secondary); padding: 6px 8px; border-bottom: 1px solid var(--border); }
    .rules td { padding: 8px; border-bottom: 1px solid var(--border); vertical-align: top; }
    .actions { white-space: nowrap; text-align: right; }
    .actions .btn + .btn { margin-left: 6px; }
    .row { display: flex; flex-wrap: wrap; gap: 6px; }
    .hint { margin: 6px 0 0; font-size: 12px; color: var(--text-secondary); }
    .muted { color: var(--text-secondary); font-size: 13px; }
    .error { color: var(--danger, #b42318); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    @media (max-width: 700px) { .rules thead { display: none; } .rules td { display: block; border: 0; padding: 4px 0; } .rules tr { display: block; padding: 8px 0; border-bottom: 1px solid var(--border); } .actions { text-align: left; } }
  `,
})
export class AdminDefaultOwner {
  private readonly api = inject(DefaultOwnerService);
  private readonly organization = inject(OrganizationService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly modes: OwnerMode[] = ['NONE', 'CREATOR', 'ROLE'];
  protected readonly modeLabels = OWNER_MODE_LABELS;
  protected readonly roles = OWNER_ROLES;
  protected readonly roleLabels = ROLE_LABELS;

  protected readonly tree = signal<TreeRow[]>([]);
  protected readonly unitId = signal<string | null>(null);
  protected readonly rules = signal<OwnerRule[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly editing = signal<string | null>(null);
  protected readonly mode = signal<OwnerMode>('NONE');
  protected readonly role = signal<string>(OWNER_ROLES[0]);

  constructor() {
    void this.load();
  }

  protected summary(rule: OwnerRule): string {
    return ruleSummary(rule);
  }

  protected indent(depth: number): string {
    return '  '.repeat(depth);
  }

  protected async chooseUnit(id: string): Promise<void> {
    this.unitId.set(id);
    this.editing.set(null);
    await this.loadRules();
  }

  protected edit(rule: OwnerRule): void {
    this.editing.set(rule.artifactType.key);
    this.mode.set(rule.mode);
    this.role.set(rule.role ?? OWNER_ROLES[0]);
  }

  protected async save(rule: OwnerRule): Promise<void> {
    const unit = this.unitId();
    if (!unit) {
      return;
    }
    await this.run(async () => {
      await this.api.save(unit, rule.artifactType.key, this.mode(), this.mode() === 'ROLE' ? this.role() : null);
      this.editing.set(null);
      await this.loadRules();
      this.snackBar.open('Responsável padrão salvo.', 'Fechar', { duration: 3000 });
    });
  }

  protected async inherit(rule: OwnerRule): Promise<void> {
    const unit = this.unitId();
    if (!unit) {
      return;
    }
    await this.run(async () => {
      await this.api.remove(unit, rule.artifactType.key);
      await this.loadRules();
      this.snackBar.open('A unidade volta a seguir a regra de cima.', 'Fechar', { duration: 3000 });
    });
  }

  private async load(): Promise<void> {
    try {
      const rows = buildTree((await this.organization.allUnits()).filter((u) => u.active));
      this.tree.set(rows);
      this.unitId.set(rows[0]?.unit.id ?? null);
      await this.loadRules();
    } catch (error) {
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async loadRules(): Promise<void> {
    const unit = this.unitId();
    this.rules.set(unit ? await this.api.rules(unit) : []);
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      this.snackBar.open(messageOf(toApiError(error)), 'Fechar', { duration: 5000 });
    } finally {
      this.busy.set(false);
    }
  }
}
