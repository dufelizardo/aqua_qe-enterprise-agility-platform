import { Component, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { ApiError, messageOf, toApiError } from '../core/api-error';
import {
  FIELD_LABELS,
  GateSummary,
  GatesService,
  ROLE_LABELS,
  RULE_LABELS,
  RuleDraft,
  RuleType,
  WorkflowInfo,
  emptyParams,
} from '../work/gates.service';
import { CustomField, CustomFieldsService } from '../work/custom-fields.service';
import { ArtifactType, WorkService } from '../work/work.service';

/** Transição do workflow escolhido, com o gate dela, se houver. */
interface TransitionRow {
  id: string;
  from: string;
  to: string;
  gate: GateSummary | null;
}

/**
 * Administração › Gates (SSD 5; #78): para cada transição de um workflow, o gate com as regras em ordem. A transição só
 * é liberada com todas as regras atendidas. Só quem gere configuração (PLATFORM_ADMIN) vê esta tela.
 */
@Component({
  selector: 'app-admin-gates',
  imports: [MatSnackBarModule, RouterLink],
  template: `
    <header class="page-header">
      <p class="eyebrow">Administração</p>
      <nav class="admin-tabs" aria-label="Seções da administração">
        <a routerLink="/admin/organizacao">Estrutura organizacional</a>
        <a routerLink="/admin/workflows" data-testid="admin-tab-workflows">Workflows</a>
        <a routerLink="/admin/gates" class="on" aria-current="page">Gates</a>
        <a routerLink="/admin/campos-customizados">Campos customizados</a>
        <a routerLink="/admin/responsavel-padrao" data-testid="admin-tab-default-owner">Responsável padrão</a>
        <a routerLink="/admin/templates-de-quadro" data-testid="admin-tab-board-templates">Templates de quadro</a>
      </nav>
      <h1 data-testid="page-title">Gates</h1>
      <p class="lead">Regras que um item precisa cumprir para mudar de status. Cada transição do fluxo pode ter um gate.</p>
    </header>

    @if (loadError(); as e) {
      <p class="card error" role="alert">{{ e }}</p>
    }

    <div class="layout">
      <section class="card" aria-label="Transições">
        <label class="field">
          Fluxo
          <select [value]="workflowId() ?? ''" (change)="chooseWorkflow($any($event.target).value)" data-testid="gates-workflow">
            @for (w of workflows(); track w.id) {
              <option [value]="w.id">{{ w.name }} ({{ w.artifactTypes.join(', ') }}){{ w.orgUnit ? ' · ' + w.orgUnit.name : '' }}</option>
            }
          </select>
        </label>
        <ul class="transitions">
          @for (t of transitions(); track t.id) {
            <li [class.on]="editing()?.transitionId === t.id" data-testid="gates-transition">
              <span class="path">{{ t.from }} → {{ t.to }}</span>
              @if (t.gate; as g) {
                <span class="chip on" data-testid="gates-transition-gate">{{ g.name }} · {{ g.rules }} regra(s)</span>
              } @else {
                <span class="chip">sem gate</span>
              }
              <button type="button" class="btn" (click)="edit(t)" data-testid="gates-configure">{{ t.gate ? 'Editar' : 'Criar gate' }}</button>
            </li>
          }
        </ul>
      </section>

      @if (editing(); as e) {
        <section class="card editor" aria-label="Gate da transição" data-testid="gates-editor">
          <h2>{{ e.label }}</h2>
          <label class="field">
            Nome do gate
            <input [value]="name()" (input)="name.set($any($event.target).value)" maxlength="120" data-testid="gates-name" />
          </label>
          <ol class="rules">
            @for (rule of rules(); track $index; let i = $index) {
              <li class="rule" data-testid="gates-rule">
                <div class="row">
                  <select [value]="rule.type" (change)="setType(i, $any($event.target).value)" aria-label="Tipo de regra" data-testid="gates-rule-type">
                    @for (t of ruleTypes; track t) {
                      <option [value]="t">{{ ruleLabels[t] }}</option>
                    }
                  </select>
                  @switch (rule.type) {
                    @case ('REQUIRED_FIELD') {
                      <select [value]="param(rule, 'field')" (change)="setParam(i, 'field', $any($event.target).value)" aria-label="Campo" data-testid="gates-param-field">
                        @for (f of fieldOptions(); track f.value) {
                          <option [value]="f.value" [selected]="f.value === param(rule, 'field')">{{ f.label }}</option>
                        }
                      </select>
                    }
                    @case ('DOCUMENT_SECTION_FILLED') {
                      <select [value]="param(rule, 'documentType')" (change)="setParam(i, 'documentType', $any($event.target).value)" aria-label="Documento" data-testid="gates-param-document">
                        @for (d of documentTypes; track d) {
                          <option [value]="d">{{ d }}</option>
                        }
                      </select>
                      <input [value]="param(rule, 'sectionKey')" (input)="setParam(i, 'sectionKey', $any($event.target).value)" placeholder="chave da seção, ex.: context" aria-label="Seção" data-testid="gates-param-section" />
                    }
                    @case ('DOCUMENT_APPROVED') {
                      <select [value]="param(rule, 'documentType')" (change)="setParam(i, 'documentType', $any($event.target).value)" aria-label="Documento" data-testid="gates-param-document">
                        @for (d of documentTypes; track d) {
                          <option [value]="d">{{ d }}</option>
                        }
                      </select>
                    }
                    @case ('MIN_CHILDREN') {
                      <select [value]="param(rule, 'childType')" (change)="setParam(i, 'childType', $any($event.target).value)" aria-label="Tipo do filho" data-testid="gates-param-child">
                        @for (t of artifactTypes(); track t.key) {
                          <option [value]="t.key">{{ t.name }}</option>
                        }
                      </select>
                      <input type="number" min="1" max="100" [value]="param(rule, 'min')" (input)="setParam(i, 'min', +$any($event.target).value)" aria-label="Mínimo" data-testid="gates-param-min" />
                    }
                    @case ('ROLE_APPROVAL') {
                      <select [value]="param(rule, 'role')" (change)="setParam(i, 'role', $any($event.target).value)" aria-label="Papel" data-testid="gates-param-role">
                        @for (r of roles; track r) {
                          <option [value]="r">{{ roleLabels[r] }}</option>
                        }
                      </select>
                      <input [value]="param(rule, 'label')" (input)="setParam(i, 'label', $any($event.target).value)" maxlength="60" placeholder="rótulo, ex.: Architecture Gate" aria-label="Rótulo" />
                    }
                  }
                </div>
                <div class="row">
                  <input class="message" [value]="rule.message" (input)="setMessage(i, $any($event.target).value)" maxlength="300" placeholder="Mensagem para quem tentar mover" aria-label="Mensagem" data-testid="gates-rule-message" />
                  <button type="button" class="btn subtle" (click)="moveRule(i, -1)" [disabled]="i === 0" aria-label="Subir regra">↑</button>
                  <button type="button" class="btn subtle" (click)="moveRule(i, 1)" [disabled]="i === rules().length - 1" aria-label="Descer regra">↓</button>
                  <button type="button" class="btn subtle" (click)="removeRule(i)" aria-label="Remover regra" data-testid="gates-rule-remove">Remover</button>
                </div>
              </li>
            }
          </ol>
          <button type="button" class="btn" (click)="addRule()" [disabled]="rules().length >= 20" data-testid="gates-add-rule">+ Regra</button>
          @if (formError()) {
            <p class="error" role="alert" data-testid="gates-error">{{ formError() }}</p>
          }
          <div class="bar">
            <button type="button" class="btn primary" (click)="save()" [disabled]="busy() || !name().trim() || rules().length === 0" data-testid="gates-save">Salvar gate</button>
            <button type="button" class="btn" (click)="editing.set(null)">Cancelar</button>
            @if (e.exists) {
              <button type="button" class="btn subtle danger" (click)="remove()" [disabled]="busy()" data-testid="gates-remove">Remover gate</button>
            }
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .page-header { margin-bottom: 16px; }
    .eyebrow { margin: 0; font-size: 12px; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); }
    h1 { margin: 4px 0; font-size: 30px; font-weight: 800; }
    .lead { margin: 0; color: var(--text-secondary); }
    .admin-tabs { display: flex; flex-wrap: wrap; gap: 12px; margin: 6px 0; }
    .admin-tabs a { color: var(--text-secondary); text-decoration: none; font-weight: 600; }
    .admin-tabs a.on { color: var(--text); border-bottom: 2px solid var(--navy-900, currentColor); }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr); gap: 16px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }
    .card { padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
    .field { display: grid; gap: 4px; font-size: 13px; font-weight: 600; margin-bottom: 12px; }
    select, input { min-height: 32px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; background: var(--surface); color: var(--text); }
    .transitions { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
    .transitions li { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 8px; border-radius: 8px; }
    .transitions li.on { background: var(--bg); }
    .path { flex: 1 1 160px; font-weight: 600; }
    .chip { font-size: 12px; padding: 1px 8px; border-radius: 999px; background: var(--bg); color: var(--text-secondary); }
    .chip.on { background: #dbeafe; color: #1e40af; }
    .rules { margin: 0 0 12px; padding-left: 20px; display: grid; gap: 12px; }
    .rule { display: grid; gap: 6px; }
    .row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .message { flex: 1 1 240px; }
    .bar { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
    .error { color: var(--danger, #b42318); }
    .danger { color: var(--danger, #b42318); }
  `,
})
export class AdminGates {
  private readonly api = inject(GatesService);
  private readonly work = inject(WorkService);
  private readonly customFieldsApi = inject(CustomFieldsService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly workflows = signal<WorkflowInfo[]>([]);
  protected readonly gateList = signal<GateSummary[]>([]);
  protected readonly artifactTypes = signal<ArtifactType[]>([]);
  protected readonly workflowId = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly editing = signal<{ transitionId: string; label: string; exists: boolean } | null>(null);
  protected readonly name = signal('');
  protected readonly rules = signal<RuleDraft[]>([]);

  protected readonly ruleTypes = Object.keys(RULE_LABELS) as RuleType[];
  protected readonly ruleLabels = RULE_LABELS;
  protected readonly customFields = signal<CustomField[]>([]);
  /**
   * Campos do "Campo preenchido": os fixos e os customizados declarados em algum tipo do workflow (#17). Para os tipos
   * que não declaram o campo, a regra não se aplica.
   */
  protected readonly fieldOptions = computed(() => {
    const w = this.workflows().find((x) => x.id === this.workflowId());
    const fixed = Object.entries(FIELD_LABELS).map(([value, label]) => ({ value, label }));
    const custom = new Map<string, string>();
    for (const f of this.customFields().filter((c) => w?.artifactTypes.includes(c.artifactType))) {
      custom.has(f.key) || custom.set(f.key, f.label);
    }
    return [...fixed, ...[...custom].map(([key, label]) => ({ value: `customFields.${key}`, label: `${label} (customizado)` }))];
  });
  protected readonly roles = Object.keys(ROLE_LABELS);
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly documentTypes = ['PRD', 'SAD', 'SSD', 'UX', 'QA'];

  protected readonly transitions = computed<TransitionRow[]>(() => {
    const w = this.workflows().find((x) => x.id === this.workflowId());
    if (!w) {
      return [];
    }
    const name = (key: string) => w.statuses.find((s) => s.key === key)?.name ?? key;
    return w.transitions.map((t) => ({
      id: t.id,
      from: name(t.from),
      to: name(t.to),
      gate: this.gateList().find((g) => g.transitionId === t.id) ?? null,
    }));
  });

  constructor() {
    void this.load();
  }

  protected chooseWorkflow(id: string): void {
    this.workflowId.set(id);
    this.editing.set(null);
  }

  protected async edit(t: TransitionRow): Promise<void> {
    this.formError.set(null);
    const label = `${t.from} → ${t.to}`;
    if (!t.gate) {
      this.name.set('');
      this.rules.set([{ type: 'REQUIRED_FIELD', params: emptyParams('REQUIRED_FIELD'), message: '' }]);
      this.editing.set({ transitionId: t.id, label, exists: false });
      return;
    }
    try {
      const gate = await this.api.gate(t.id);
      this.name.set(gate.name);
      this.rules.set(gate.rules.map((r) => ({ type: r.type, params: { ...r.params }, message: r.message })));
      this.editing.set({ transitionId: t.id, label, exists: true });
    } catch (error) {
      this.formError.set(messageOf(toApiError(error)));
    }
  }

  protected param(rule: RuleDraft, key: string): string {
    const value = rule.params[key];
    return value === undefined || value === null ? '' : String(value);
  }

  protected setType(i: number, type: RuleType): void {
    this.update(i, (r) => ({ ...r, type, params: emptyParams(type) }));
  }

  protected setParam(i: number, key: string, value: unknown): void {
    this.update(i, (r) => ({ ...r, params: { ...r.params, [key]: value } }));
  }

  protected setMessage(i: number, message: string): void {
    this.update(i, (r) => ({ ...r, message }));
  }

  protected addRule(): void {
    this.rules.set([...this.rules(), { type: 'REQUIRED_FIELD', params: emptyParams('REQUIRED_FIELD'), message: '' }]);
  }

  protected removeRule(i: number): void {
    this.rules.set(this.rules().filter((_, index) => index !== i));
  }

  protected moveRule(i: number, step: number): void {
    const list = [...this.rules()];
    const target = i + step;
    if (target < 0 || target >= list.length) {
      return;
    }
    [list[i], list[target]] = [list[target]!, list[i]!];
    this.rules.set(list);
  }

  protected async save(): Promise<void> {
    const e = this.editing();
    if (!e) {
      return;
    }
    await this.run(async () => {
      await this.api.save(e.transitionId, this.name().trim(), this.rules());
      this.gateList.set(await this.api.gates());
      this.editing.set({ ...e, exists: true });
      this.snackBar.open('Gate salvo.', undefined, { duration: 2500 });
    });
  }

  protected async remove(): Promise<void> {
    const e = this.editing();
    if (!e || !window.confirm('Remover o gate desta transição? Os itens passam a mudar de status sem as regras.')) {
      return;
    }
    await this.run(async () => {
      await this.api.remove(e.transitionId);
      this.gateList.set(await this.api.gates());
      this.editing.set(null);
      this.snackBar.open('Gate removido.', undefined, { duration: 2500 });
    });
  }

  private update(i: number, change: (r: RuleDraft) => RuleDraft): void {
    this.rules.set(this.rules().map((r, index) => (index === i ? change(r) : r)));
  }

  private async load(): Promise<void> {
    try {
      const [workflows, gates, types, customFields] = await Promise.all([
        this.api.workflows(),
        this.api.gates(),
        this.work.artifactTypes(),
        this.customFieldsApi.list(),
      ]);
      this.customFields.set(customFields);
      this.workflows.set(workflows);
      this.gateList.set(gates);
      this.artifactTypes.set(types);
      this.workflowId.set(workflows[0]?.id ?? null);
    } catch (error) {
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.formError.set(null);
    try {
      await action();
    } catch (error) {
      this.formError.set(gateErrorText(toApiError(error)));
    } finally {
      this.busy.set(false);
    }
  }
}

/** Mensagem do erro ao salvar, com a regra que falhou ("Regra 2: …"). */
export function gateErrorText(error: ApiError): string {
  const first = error.errors?.[0];
  if (!first) {
    return messageOf(error);
  }
  const match = /^rules\[(\d+)\]/.exec(first.field);
  return match ? `Regra ${Number(match[1]) + 1}: ${first.message}` : `${first.field}: ${first.message}`;
}
