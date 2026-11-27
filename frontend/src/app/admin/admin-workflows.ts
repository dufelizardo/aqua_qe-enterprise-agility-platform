import { Component, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { messageOf, toApiError } from '../core/api-error';
import { WorkService } from '../work/work.service';
import {
  CATEGORY_LABELS,
  Category,
  Draft,
  DraftStatus,
  MigrationPreview,
  PHASE_LABELS,
  Phase,
  UnitWorkflow,
  WorkflowDesignerService,
  WorkflowVersion,
  draftOf,
  keyFromName,
  removeStatus,
  removedKeys,
} from '../work/workflow-designer.service';
import { OrganizationService, TreeRow, buildTree } from './organization.service';

/**
 * Administração › Workflows (#49; UI-26; SSD 3.3; UX/UI 7.10). Para a unidade escolhida, os workflows vigentes e de
 * onde vêm; personalizar (cópia do herdado, que passa a valer para ela e para as de baixo), editar e publicar uma nova
 * versão, voltar a herdar e migrar, de forma explícita, os itens que estão em outra versão.
 */
@Component({
  selector: 'app-admin-workflows',
  imports: [MatSnackBarModule, RouterLink],
  template: `
    <header class="page-header">
      <p class="eyebrow">Administração</p>
      <nav class="admin-tabs" aria-label="Seções da administração">
        <a routerLink="/admin/organizacao">Estrutura organizacional</a>
        <a routerLink="/admin/workflows" class="on" aria-current="page">Workflows</a>
        <a routerLink="/admin/gates">Gates</a>
        <a routerLink="/admin/responsavel-padrao">Responsável padrão</a>
      </nav>
      <h1 data-testid="page-title">Workflows</h1>
      <p class="lead">
        Status e transições de cada tipo de item. Uma unidade pode ter o próprio workflow, que vale para ela e para as unidades
        abaixo. Publicar uma versão não move os itens: a migração é sempre explícita.
      </p>
    </header>

    @if (loadError(); as e) {
      <p class="card error" role="alert">{{ e }}</p>
    }

    <section class="card">
      <label class="field">
        Unidade
        <select (change)="chooseUnit($any($event.target).value)" data-testid="wf-unit">
          @for (row of tree(); track row.unit.id) {
            <option [value]="row.unit.id" [selected]="row.unit.id === unitId()" [attr.data-name]="row.unit.name">
              {{ indent(row.depth) }}{{ row.unit.name }} ({{ row.unit.level.name }})
            </option>
          }
        </select>
      </label>

      <ul class="workflows" aria-label="Workflows vigentes na unidade">
        @for (item of workflows(); track item.workflow.id) {
          <li class="workflow" data-testid="wf-row" [attr.data-name]="item.workflow.name">
            <div class="head">
              <div>
                <strong data-testid="wf-name">{{ item.workflow.name }}</strong>
                <span class="muted" data-testid="wf-version">v{{ item.workflow.versionNumber }}</span>
                <p class="muted">{{ typeNames(item.workflow.artifactTypes) }}</p>
                <p class="muted" data-testid="wf-origin">
                  @if (!item.inherited) {
                    Desta unidade
                  } @else {
                    Herdado de {{ item.source?.name }}
                  }
                </p>
              </div>
              <div class="actions">
                @if (item.inherited) {
                  <button type="button" class="btn" [disabled]="busy()" (click)="customize(item)" data-testid="wf-customize">
                    Personalizar nesta unidade
                  </button>
                } @else {
                  <button type="button" class="btn" [disabled]="busy()" (click)="edit(item.workflow)" data-testid="wf-edit">Editar</button>
                  @if (!isRoot()) {
                    <button type="button" class="btn" [disabled]="busy()" (click)="askRevert(item.workflow)" data-testid="wf-revert">
                      Voltar a herdar
                    </button>
                  }
                }
              </div>
            </div>

            @if (!item.inherited && pendingOf(item.workflow.id); as pending) {
              <p class="notice" data-testid="wf-pending">
                {{ pending.total }} {{ pending.total === 1 ? 'item está' : 'itens estão' }} em outra versão de workflow.
                <button type="button" class="btn" (click)="openMigration(item.workflow)" data-testid="wf-migrate">Migrar itens</button>
              </p>
            }

            @if (reverting() === item.workflow.id) {
              <div class="notice confirm" role="alertdialog" data-testid="wf-revert-confirm">
                <p>
                  <strong>Voltar a herdar?</strong> A unidade passa a usar o workflow de cima. Os itens continuam nesta versão até você
                  migrá-los.
                </p>
                <button type="button" class="btn danger" [disabled]="busy()" (click)="revert(item.workflow)" data-testid="wf-revert-ok">
                  Voltar a herdar
                </button>
                <button type="button" class="btn" (click)="reverting.set(null)">Cancelar</button>
              </div>
            }

            @if (editingId() === item.workflow.id && draft(); as d) {
              <form class="editor" data-testid="wf-editor" (submit)="$event.preventDefault(); askPublish()">
                <label class="field">
                  Nome
                  <input [value]="d.name" (input)="setName($any($event.target).value)" maxlength="120" data-testid="wf-edit-name" />
                </label>

                <h3>Status</h3>
                <p class="hint">A ordem é a da sequência. Status fora da sequência (como Cancelado) não viram coluna do quadro.</p>
                <table class="statuses" aria-label="Status do workflow">
                  <thead>
                    <tr>
                      <th scope="col">Nome</th><th scope="col">Chave</th><th scope="col">Fase</th><th scope="col">Categoria</th>
                      <th scope="col">Na sequência</th><th scope="col"><span class="sr-only">Ações</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (s of d.statuses; track s.key; let i = $index; let last = $last) {
                      <tr data-testid="wf-status-row" [attr.data-key]="s.key">
                        <td>
                          <input [value]="s.name" (input)="setStatus(i, 'name', $any($event.target).value)" maxlength="80"
                                 [attr.aria-label]="'Nome de ' + s.key" data-testid="wf-status-name" />
                        </td>
                        <td><code>{{ s.key }}</code></td>
                        <td>
                          <select (change)="setStatus(i, 'phase', $any($event.target).value || null)" [attr.aria-label]="'Fase de ' + s.name">
                            <option value="" [selected]="!s.phase">—</option>
                            @for (p of phases; track p) {
                              <option [value]="p" [selected]="s.phase === p">{{ phaseLabels[p] }}</option>
                            }
                          </select>
                        </td>
                        <td>
                          <select (change)="setStatus(i, 'category', $any($event.target).value)" [attr.aria-label]="'Categoria de ' + s.name"
                                  data-testid="wf-status-category">
                            @for (c of categories; track c) {
                              <option [value]="c" [selected]="s.category === c">{{ categoryLabels[c] }}</option>
                            }
                          </select>
                        </td>
                        <td>
                          <input type="checkbox" [checked]="s.inSequence" (change)="setStatus(i, 'inSequence', $any($event.target).checked)"
                                 [attr.aria-label]="s.name + ' na sequência'" />
                        </td>
                        <td class="actions">
                          <button type="button" class="btn icon" [disabled]="i === 0" (click)="moveStatus(i, -1)" [attr.aria-label]="'Subir ' + s.name">↑</button>
                          <button type="button" class="btn icon" [disabled]="last" (click)="moveStatus(i, 1)" [attr.aria-label]="'Descer ' + s.name">↓</button>
                          <button type="button" class="btn icon" (click)="dropStatus(s.key)" [attr.aria-label]="'Remover ' + s.name"
                                  data-testid="wf-status-remove">×</button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
                <div class="row">
                  <input [value]="newStatusName()" (input)="newStatusName.set($any($event.target).value)" maxlength="80"
                         placeholder="Nome do novo status" aria-label="Nome do novo status" data-testid="wf-new-status-name"
                         (keydown.enter)="$event.preventDefault(); addStatus()" />
                  <button type="button" class="btn" [disabled]="!newStatusName().trim()" (click)="addStatus()" data-testid="wf-add-status">
                    Adicionar status
                  </button>
                </div>

                <h3>Transições</h3>
                <p class="hint">Marque de qual status (linha) se pode ir para qual (coluna).</p>
                <div class="matrix-wrap">
                  <table class="matrix" aria-label="Transições permitidas">
                    <thead>
                      <tr>
                        <th scope="col">De \\ Para</th>
                        @for (to of d.statuses; track to.key) {
                          <th scope="col">{{ to.name }}</th>
                        }
                      </tr>
                    </thead>
                    <tbody>
                      @for (from of d.statuses; track from.key) {
                        <tr>
                          <th scope="row">{{ from.name }}</th>
                          @for (to of d.statuses; track to.key) {
                            <td>
                              @if (from.key !== to.key) {
                                <input type="checkbox" [checked]="hasTransition(from.key, to.key)"
                                       (change)="toggleTransition(from.key, to.key, $any($event.target).checked)"
                                       [attr.aria-label]="from.name + ' para ' + to.name"
                                       [attr.data-testid]="'wf-transition-' + from.key + '-' + to.key" />
                              }
                            </td>
                          }
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>

                @if (confirmingPublish()) {
                  <div class="notice confirm" role="alertdialog" data-testid="wf-publish-confirm">
                    <p>
                      <strong>Publicar a versão {{ item.workflow.versionNumber + 1 }}?</strong> Os itens novos já nascem nela. Os
                      existentes continuam na versão atual até você migrá-los.
                    </p>
                    @if (removed().length > 0) {
                      <p data-testid="wf-publish-removed">
                        Status removidos: {{ removed().join(', ') }}. Na migração, os itens neles vão precisar de um status de destino.
                      </p>
                    }
                    <button type="button" class="btn primary" [disabled]="busy()" (click)="publish(item.workflow)" data-testid="wf-publish-ok">
                      Publicar
                    </button>
                    <button type="button" class="btn" (click)="confirmingPublish.set(false)">Voltar</button>
                  </div>
                } @else {
                  <div class="row">
                    <button type="submit" class="btn primary" [disabled]="busy()" data-testid="wf-publish">Publicar nova versão</button>
                    <button type="button" class="btn" (click)="closeEditor()">Cancelar</button>
                  </div>
                }
                @if (formError(); as e) {
                  <p class="field-error" role="alert" data-testid="wf-error">{{ e }}</p>
                }
              </form>
            }

            @if (migratingId() === item.workflow.id && migration(); as m) {
              <section class="editor" data-testid="wf-migration" aria-label="Migrar itens">
                <h3>Migrar para a versão {{ m.versionNumber }}</h3>
                <p class="hint">
                  Escolha o status de destino de cada status atual. A mesma chave já vem marcada quando existe na versão {{ m.versionNumber }}.
                  As aprovações dadas no status antigo não acompanham o item.
                </p>
                <table class="statuses" aria-label="Itens por status atual">
                  <thead>
                    <tr><th scope="col">Status atual</th><th scope="col">Itens</th><th scope="col">Vai para</th></tr>
                  </thead>
                  <tbody>
                    @for (p of m.pending; track p.statusKey) {
                      <tr [attr.data-testid]="'wf-migration-row-' + p.statusKey">
                        <td>{{ p.statusName }} <code>{{ p.statusKey }}</code></td>
                        <td>{{ p.items }}</td>
                        <td>
                          <select (change)="setTarget(p.statusKey, $any($event.target).value)" [attr.aria-label]="'Destino de ' + p.statusName"
                                  [attr.data-testid]="'wf-migration-target-' + p.statusKey">
                            <option value="" [selected]="!mapping()[p.statusKey]">Escolha…</option>
                            @for (t of m.targets; track t.key) {
                              <option [value]="t.key" [selected]="mapping()[p.statusKey] === t.key">{{ t.name }}</option>
                            }
                          </select>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
                <div class="row">
                  <button type="button" class="btn primary" [disabled]="busy() || !mappingComplete()" (click)="migrate(item.workflow)"
                          data-testid="wf-migration-ok">
                    Migrar {{ m.total }} {{ m.total === 1 ? 'item' : 'itens' }}
                  </button>
                  <button type="button" class="btn" (click)="migratingId.set(null)">Cancelar</button>
                </div>
              </section>
            }
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    .page-header { margin-bottom: 16px; }
    .eyebrow { margin: 0; font-size: 12px; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); }
    h1 { margin: 4px 0; font-size: 30px; font-weight: 800; }
    h3 { margin: 16px 0 4px; font-size: 15px; }
    .lead { margin: 0; color: var(--text-secondary); max-width: 760px; }
    .admin-tabs { display: flex; flex-wrap: wrap; gap: 12px; margin: 6px 0; }
    .admin-tabs a { color: var(--text-secondary); text-decoration: none; font-weight: 600; }
    .admin-tabs a.on { color: var(--text); border-bottom: 2px solid var(--navy-900, currentColor); }
    .card { padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
    .field { display: grid; gap: 4px; font-size: 13px; font-weight: 600; margin-bottom: 12px; max-width: 480px; }
    select, input:not([type='checkbox']) { min-height: 32px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; background: var(--surface); color: var(--text); }
    .workflows { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
    .workflow { border: 1px solid var(--border); border-radius: 10px; padding: 12px; }
    .head { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; }
    .head p { margin: 2px 0 0; }
    .actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: flex-start; }
    .row { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
    .muted { color: var(--text-secondary); font-size: 13px; }
    .hint { margin: 0 0 6px; font-size: 12px; color: var(--text-secondary); }
    .notice { margin: 10px 0 0; padding: 10px 12px; border-radius: 8px; background: #fff7e6; color: var(--text); }
    .notice.confirm p { margin: 0 0 8px; }
    .notice .btn { margin-left: 6px; }
    .editor { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border); }
    .statuses { width: 100%; border-collapse: collapse; }
    .statuses th { text-align: left; font-size: 12px; color: var(--text-secondary); padding: 4px 6px; border-bottom: 1px solid var(--border); }
    .statuses td { padding: 4px 6px; border-bottom: 1px solid var(--border); }
    .matrix-wrap { overflow-x: auto; max-width: 100%; }
    .matrix { border-collapse: collapse; font-size: 12px; }
    .matrix th, .matrix td { padding: 4px 6px; border: 1px solid var(--border); text-align: center; }
    .matrix th[scope='row'] { text-align: left; white-space: nowrap; }
    .btn.icon { min-width: 28px; padding: 0 6px; }
    .field-error { color: var(--danger-700, #b42318); font-size: 13px; }
    .error { color: var(--danger, #b42318); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    @media (max-width: 700px) { .statuses thead { display: none; } .statuses td { display: block; border: 0; } .statuses tr { display: block; padding: 6px 0; border-bottom: 1px solid var(--border); } }
  `,
})
export class AdminWorkflows {
  private readonly api = inject(WorkflowDesignerService);
  private readonly organization = inject(OrganizationService);
  private readonly work = inject(WorkService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly phases: Phase[] = ['UPSTREAM', 'DOWNSTREAM'];
  protected readonly categories: Category[] = ['TODO', 'IN_PROGRESS', 'DONE'];
  protected readonly phaseLabels = PHASE_LABELS;
  protected readonly categoryLabels = CATEGORY_LABELS;

  protected readonly tree = signal<TreeRow[]>([]);
  protected readonly unitId = signal<string | null>(null);
  protected readonly isRoot = computed(() => this.tree().find((r) => r.unit.id === this.unitId())?.unit.parentId === null);
  protected readonly workflows = signal<UnitWorkflow[]>([]);
  protected readonly pending = signal<Record<string, MigrationPreview>>({});
  protected readonly typeLabels = signal<Record<string, string>>({});
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);

  // Edição
  protected readonly editingId = signal<string | null>(null);
  protected readonly published = signal<WorkflowVersion | null>(null);
  protected readonly draft = signal<Draft | null>(null);
  protected readonly newStatusName = signal('');
  protected readonly confirmingPublish = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly removed = computed(() => {
    const published = this.published();
    const draft = this.draft();
    return published && draft ? removedKeys(published, draft) : [];
  });

  // Voltar a herdar e migração
  protected readonly reverting = signal<string | null>(null);
  protected readonly migratingId = signal<string | null>(null);
  protected readonly migration = signal<MigrationPreview | null>(null);
  protected readonly mapping = signal<Record<string, string>>({});
  protected readonly mappingComplete = computed(() => {
    const m = this.migration();
    return !!m && m.pending.every((p) => !!this.mapping()[p.statusKey]);
  });

  constructor() {
    void this.load();
  }

  protected indent(depth: number): string {
    return '  '.repeat(depth);
  }

  protected typeNames(keys: string[]): string {
    return keys.map((k) => this.typeLabels()[k] ?? k).join(', ');
  }

  protected pendingOf(workflowId: string): MigrationPreview | null {
    const preview = this.pending()[workflowId];
    return preview && preview.total > 0 ? preview : null;
  }

  protected async chooseUnit(id: string): Promise<void> {
    this.unitId.set(id);
    this.closeEditor();
    this.reverting.set(null);
    this.migratingId.set(null);
    await this.loadWorkflows();
  }

  protected async customize(item: UnitWorkflow): Promise<void> {
    const unit = this.unitId();
    if (!unit) {
      return;
    }
    await this.run(async () => {
      const created = await this.api.customize(unit, item.workflow.id);
      await this.loadWorkflows();
      this.edit(created);
      this.toast('Workflow copiado para a unidade. Edite e publique uma nova versão.');
    });
  }

  protected edit(workflow: WorkflowVersion): void {
    this.migratingId.set(null);
    this.reverting.set(null);
    this.editingId.set(workflow.id);
    this.published.set(workflow);
    this.draft.set(draftOf(workflow));
    this.newStatusName.set('');
    this.confirmingPublish.set(false);
    this.formError.set(null);
  }

  protected closeEditor(): void {
    this.editingId.set(null);
    this.draft.set(null);
    this.published.set(null);
    this.confirmingPublish.set(false);
    this.formError.set(null);
  }

  protected setName(name: string): void {
    this.update((d) => ({ ...d, name }));
  }

  protected setStatus<K extends keyof DraftStatus>(index: number, field: K, value: DraftStatus[K]): void {
    this.update((d) => ({ ...d, statuses: d.statuses.map((s, i) => (i === index ? { ...s, [field]: value } : s)) }));
  }

  protected moveStatus(index: number, delta: number): void {
    this.update((d) => {
      const statuses = [...d.statuses];
      const [moved] = statuses.splice(index, 1);
      statuses.splice(index + delta, 0, moved);
      return { ...d, statuses };
    });
  }

  protected dropStatus(key: string): void {
    this.update((d) => removeStatus(d, key));
  }

  protected addStatus(): void {
    const name = this.newStatusName().trim();
    if (!name) {
      return;
    }
    this.update((d) => {
      const key = keyFromName(name, d.statuses.map((s) => s.key));
      const status: DraftStatus = { key, name, phase: null, category: 'IN_PROGRESS', inSequence: true, isNew: true };
      // Entra antes dos status fora da sequência (Cancelado), no fim da sequência.
      const firstOut = d.statuses.findIndex((s) => !s.inSequence);
      const statuses = [...d.statuses];
      statuses.splice(firstOut < 0 ? statuses.length : firstOut, 0, status);
      return { ...d, statuses };
    });
    this.newStatusName.set('');
  }

  protected hasTransition(from: string, to: string): boolean {
    return this.draft()?.transitions.has(`${from}>${to}`) ?? false;
  }

  protected toggleTransition(from: string, to: string, on: boolean): void {
    this.update((d) => {
      const transitions = new Set(d.transitions);
      if (on) {
        transitions.add(`${from}>${to}`);
      } else {
        transitions.delete(`${from}>${to}`);
      }
      return { ...d, transitions };
    });
  }

  protected askPublish(): void {
    this.formError.set(null);
    this.confirmingPublish.set(true);
  }

  protected async publish(workflow: WorkflowVersion): Promise<void> {
    const draft = this.draft();
    if (!draft) {
      return;
    }
    this.busy.set(true);
    try {
      const created = await this.api.publish(workflow.id, draft);
      this.closeEditor();
      await this.loadWorkflows();
      this.toast(`Versão ${created.versionNumber} publicada.`);
    } catch (error) {
      this.confirmingPublish.set(false);
      const api = toApiError(error);
      const field = api.errors?.[0];
      this.formError.set(field ? `${this.fieldLabel(field.field)}: ${field.message}` : messageOf(api));
    } finally {
      this.busy.set(false);
    }
  }

  protected askRevert(workflow: WorkflowVersion): void {
    this.closeEditor();
    this.migratingId.set(null);
    this.reverting.set(workflow.id);
  }

  protected async revert(workflow: WorkflowVersion): Promise<void> {
    await this.run(async () => {
      await this.api.revert(workflow.id);
      this.reverting.set(null);
      await this.loadWorkflows();
      this.toast('A unidade volta a seguir o workflow de cima.');
    });
  }

  protected async openMigration(workflow: WorkflowVersion): Promise<void> {
    await this.run(async () => {
      this.closeEditor();
      this.reverting.set(null);
      const preview = await this.api.migrationPreview(workflow.id);
      const mapping: Record<string, string> = {};
      preview.pending.forEach((p) => p.suggestedKey && (mapping[p.statusKey] = p.suggestedKey));
      this.mapping.set(mapping);
      this.migration.set(preview);
      this.migratingId.set(workflow.id);
    });
  }

  protected setTarget(statusKey: string, target: string): void {
    this.mapping.update((m) => ({ ...m, [statusKey]: target }));
  }

  protected async migrate(workflow: WorkflowVersion): Promise<void> {
    await this.run(async () => {
      const result = await this.api.migrate(workflow.id, this.mapping());
      this.migratingId.set(null);
      await this.loadWorkflows();
      this.toast(`${result.migrated} ${result.migrated === 1 ? 'item migrado' : 'itens migrados'}.`);
    });
  }

  private update(change: (draft: Draft) => Draft): void {
    const draft = this.draft();
    if (draft) {
      this.draft.set(change(draft));
    }
  }

  private fieldLabel(field: string): string {
    const match = /^statuses\[(\d+)\]/.exec(field);
    if (match) {
      const status = this.draft()?.statuses[Number(match[1])];
      return status ? `Status ${status.name}` : 'Status';
    }
    return field.startsWith('transitions') ? 'Transições' : field === 'name' ? 'Nome' : 'Status';
  }

  private async load(): Promise<void> {
    try {
      const [units, types] = await Promise.all([this.organization.allUnits(), this.work.artifactTypes()]);
      this.typeLabels.set(Object.fromEntries(types.map((t) => [t.key, t.name])));
      const rows = buildTree(units.filter((u) => u.active));
      this.tree.set(rows);
      this.unitId.set(rows[0]?.unit.id ?? null);
      await this.loadWorkflows();
    } catch (error) {
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async loadWorkflows(): Promise<void> {
    const unit = this.unitId();
    const items = unit ? await this.api.ofUnit(unit) : [];
    this.workflows.set(items);
    const own = items.filter((i) => !i.inherited);
    const previews = await Promise.all(own.map((i) => this.api.migrationPreview(i.workflow.id)));
    this.pending.set(Object.fromEntries(previews.map((p) => [p.workflowId, p])));
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      this.toast(messageOf(toApiError(error)), 5000);
    } finally {
      this.busy.set(false);
    }
  }

  private toast(message: string, duration = 3000): void {
    this.snackBar.open(message, 'Fechar', { duration });
  }
}
