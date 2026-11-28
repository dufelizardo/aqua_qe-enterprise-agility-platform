import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';

import { messageOf, toApiError } from '../../core/api-error';
import { GatesService, PanelGate, PanelRule, approversText, ruleSummary } from '../../work/gates.service';
import { Artifact, TransitionOption, WorkService } from '../../work/work.service';

/** Próximo passo: a transição possível e o gate dela, se houver. */
interface Step {
  option: TransitionOption;
  gate: PanelGate | null;
}

/**
 * Próximos passos do artefato (UX/UI 6.4, 7.4 e 7.6; #78): cada transição que sai do status atual, com o checklist do
 * gate (o que já está atendido e o que falta), as decisões das regras de aprovação e o botão de mover, liberado só com
 * tudo atendido.
 */
@Component({
  selector: 'app-artifact-gates',
  imports: [DatePipe, RouterLink],
  template: `
    @if (steps().length > 0) {
      <div class="card gates" data-testid="artifact-gates">
        <h2>Próximos passos</h2>
        @if (error()) {
          <p class="error" role="alert" data-testid="gates-error">{{ error() }}</p>
        }
        @for (step of steps(); track step.option.id) {
          <section class="step" [attr.data-testid]="'gate-step-' + step.option.to.key">
            <header>
              <span class="to">→ {{ step.option.to.name }}</span>
              @if (step.gate; as g) {
                <span class="gate-name">{{ g.name }}</span>
                <span class="count" [class.done]="g.satisfied === g.total" data-testid="gate-progress">{{ g.satisfied }}/{{ g.total }}</span>
              }
              @if (step.option.canExecute) {
                <button type="button" class="btn primary" [disabled]="busy() || step.option.failedRules.length > 0" (click)="move(step.option)"
                  [title]="step.option.failedRules.length > 0 ? 'Atenda às regras do gate para mover' : ''" data-testid="gate-move">Mover</button>
              }
            </header>
            @if (step.gate; as g) {
              <ul>
                @for (rule of g.rules; track rule.ruleId) {
                  <li [class.ok]="rule.satisfied" data-testid="gate-rule">
                    <span class="mark" aria-hidden="true">{{ rule.satisfied ? '✓' : '○' }}</span>
                    <div class="text">
                      <span [attr.aria-label]="(rule.satisfied ? 'Atendida: ' : 'Pendente: ') + rule.message">{{ rule.message }}</span>
                      <span class="muted">{{ summary(rule) }}
                        @if (!rule.satisfied && documentOf(rule); as doc) {
                          · <a [routerLink]="[]" [queryParams]="{ tab: doc }" data-testid="gate-open-document">Abrir {{ doc }}</a>
                        }
                      </span>
                      @if (approvers(rule); as who) {
                        <span class="muted" data-testid="gate-approvers">{{ who }}</span>
                      }
                      @if (rule.lastDecision; as d) {
                        <span class="muted" data-testid="gate-decision">
                          {{ d.decision === 'APPROVED' ? 'Aprovada' : 'Rejeitada' }} por {{ d.by.name ?? 'alguém' }} em {{ d.at | date: 'dd/MM/yyyy HH:mm' }}@if (d.comment) {: "{{ d.comment }}"}
                        </span>
                      }
                      @if (rule.canDecide) {
                        @if (deciding()?.ruleId === rule.ruleId) {
                          <form class="decide" (submit)="confirm($event, rule)">
                            <input [value]="comment()" (input)="comment.set($any($event.target).value)" maxlength="1000"
                              [placeholder]="deciding()?.decision === 'REJECTED' ? 'Motivo da rejeição (obrigatório)' : 'Comentário (opcional)'"
                              aria-label="Comentário da decisão" data-testid="gate-comment" />
                            <button type="submit" class="btn primary" [disabled]="busy() || (deciding()?.decision === 'REJECTED' && !comment().trim())" data-testid="gate-confirm">
                              {{ deciding()?.decision === 'APPROVED' ? 'Aprovar' : 'Rejeitar' }}
                            </button>
                            <button type="button" class="btn" (click)="deciding.set(null)">Cancelar</button>
                          </form>
                        } @else {
                          <div class="actions">
                            <button type="button" class="btn" (click)="startDecision(rule, 'APPROVED')" data-testid="gate-approve">Aprovar</button>
                            <button type="button" class="btn subtle" (click)="startDecision(rule, 'REJECTED')" data-testid="gate-reject">Rejeitar</button>
                          </div>
                        }
                      }
                    </div>
                  </li>
                }
              </ul>
            } @else if (step.option.failedRules.length > 0) {
              <ul>
                @for (f of step.option.failedRules; track f.ruleId) {
                  <li><span class="mark" aria-hidden="true">○</span><span>{{ f.message }}</span></li>
                }
              </ul>
            }
          </section>
        }
      </div>
    }
  `,
  styles: `
    .gates h2 { margin-top: 0; }
    .step { padding: 10px 0; border-top: 1px solid var(--border); }
    .step:first-of-type { border-top: none; }
    header { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .to { font-weight: 700; color: var(--text); }
    .gate-name { color: var(--text-secondary); font-size: 13px; }
    .count { font-size: 12px; font-weight: 700; padding: 1px 8px; border-radius: 999px; background: var(--bg); color: var(--text-secondary); }
    .count.done { background: #dcfae6; color: #067647; }
    header .btn { margin-left: auto; }
    ul { list-style: none; margin: 8px 0 0; padding: 0; display: grid; gap: 8px; }
    li { display: flex; gap: 8px; align-items: flex-start; }
    .mark { flex: none; width: 18px; text-align: center; font-weight: 700; color: var(--text-secondary); }
    li.ok .mark { color: #067647; }
    .text { display: grid; gap: 2px; min-width: 0; }
    .muted { font-size: 12px; color: var(--text-secondary); overflow-wrap: anywhere; }
    .actions, .decide { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 4px; }
    .decide input { flex: 1 1 200px; min-height: 30px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; }
    .error { color: var(--danger, #b42318); }
  `,
})
export class ArtifactGates {
  readonly artifact = input.required<Artifact>();
  /** O artefato mudou de status: a página recarrega. */
  readonly moved = output<void>();

  private readonly gates = inject(GatesService);
  private readonly work = inject(WorkService);

  private readonly options = signal<TransitionOption[]>([]);
  private readonly panel = signal<PanelGate[]>([]);
  protected readonly steps = computed<Step[]>(() =>
    this.options().map((option) => ({ option, gate: this.panel().find((g) => g.transitionId === option.id) ?? null })),
  );
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly deciding = signal<{ ruleId: string; decision: 'APPROVED' | 'REJECTED' } | null>(null);
  protected readonly comment = signal('');
  /** Quem pode aprovar, enquanto a regra está pendente (#14). */
  protected approvers(rule: PanelRule): string {
    return rule.satisfied ? '' : approversText(rule);
  }

  protected readonly summary = ruleSummary;

  constructor() {
    effect(() => {
      const a = this.artifact();
      untracked(() => void this.load(a.key));
    });
  }

  /** Regra de documento: o tipo (PRD, SAD…), para o link que abre a aba do documento. */
  protected documentOf(rule: PanelRule): string | null {
    return rule.type === 'DOCUMENT_APPROVED' || rule.type === 'DOCUMENT_SECTION_FILLED'
      ? String(rule.params['documentType'] ?? '') || null
      : null;
  }

  protected startDecision(rule: PanelRule, decision: 'APPROVED' | 'REJECTED'): void {
    this.comment.set('');
    this.deciding.set({ ruleId: rule.ruleId, decision });
  }

  protected async confirm(event: Event, rule: PanelRule): Promise<void> {
    event.preventDefault();
    const d = this.deciding();
    if (!d) {
      return;
    }
    await this.run(async () => {
      this.panel.set(await this.gates.decide(this.artifact().key, rule.ruleId, d.decision, this.comment().trim() || null));
      this.options.set(await this.work.transitions(this.artifact().key));
      this.deciding.set(null);
    });
  }

  protected async move(option: TransitionOption): Promise<void> {
    await this.run(async () => {
      await this.work.transition(this.artifact().key, option.id, this.artifact().version);
      this.moved.emit();
    });
  }

  private async load(key: string): Promise<void> {
    this.error.set(null);
    try {
      const [options, panel] = await Promise.all([this.work.transitions(key), this.gates.panel(key)]);
      this.options.set(options);
      this.panel.set(panel);
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    }
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      const apiError = toApiError(error);
      this.error.set(apiError.code === 'GATE_BLOCKED' ? `Bloqueado pelo gate: ${apiError.detail}` : messageOf(apiError));
      await this.load(this.artifact().key);
    } finally {
      this.busy.set(false);
    }
  }
}
