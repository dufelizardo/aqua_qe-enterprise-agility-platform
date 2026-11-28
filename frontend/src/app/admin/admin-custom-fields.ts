import { Component, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { messageOf, toApiError } from '../core/api-error';
import {
  CustomField,
  CustomFieldType,
  CustomFieldsService,
  FIELD_TYPE_LABELS,
  keyFromLabel,
  parseOptions,
} from '../work/custom-fields.service';
import { ArtifactType, WorkService } from '../work/work.service';

/**
 * Administração › Campos customizados (#17; REV-12): os campos de cada tipo de artefato, com rótulo, chave e tipo. Só os
 * declarados podem ser preenchidos nos itens e citados num gate. A chave e o tipo ficam como foram criados; um campo
 * citado por um gate não pode ser removido.
 */
@Component({
  selector: 'app-admin-custom-fields',
  imports: [MatSnackBarModule, RouterLink],
  template: `
    <header class="page-header">
      <p class="eyebrow">Administração</p>
      <nav class="admin-tabs" aria-label="Seções da administração">
        <a routerLink="/admin/organizacao">Estrutura organizacional</a>
        <a routerLink="/admin/workflows">Workflows</a>
        <a routerLink="/admin/gates">Gates</a>
        <a routerLink="/admin/campos-customizados" class="on" aria-current="page">Campos customizados</a>
        <a routerLink="/admin/responsavel-padrao">Responsável padrão</a>
        <a routerLink="/admin/templates-de-quadro">Templates de quadro</a>
        <a routerLink="/admin/pessoas">Pessoas e privacidade</a>
      </nav>
      <h1 data-testid="page-title">Campos customizados</h1>
      <p class="lead">
        Campos a mais em cada tipo de item, valendo para a organização inteira. Aparecem na página do item e podem ser
        exigidos num gate ("Campo preenchido"). A chave e o tipo não mudam depois de criados.
      </p>
    </header>

    @if (loadError(); as e) {
      <p class="card error" role="alert">{{ e }}</p>
    }

    <section class="card">
      <label class="field">
        Tipo de item
        <select [value]="typeKey()" (change)="chooseType($any($event.target).value)" data-testid="cf-type">
          @for (t of types(); track t.key) {
            <option [value]="t.key" [selected]="t.key === typeKey()">{{ t.name }} ({{ t.level }})</option>
          }
        </select>
      </label>

      <ul class="fields">
        @for (f of fields(); track f.id; let first = $first; let last = $last) {
          <li class="row" data-testid="cf-row" [attr.data-key]="f.key">
            @if (editingId() === f.id) {
              <div class="edit">
                <input [value]="editLabel()" (input)="editLabel.set($any($event.target).value)" maxlength="80"
                       aria-label="Rótulo" data-testid="cf-edit-label" />
                @if (f.type === 'SELECT') {
                  <textarea rows="3" [value]="editOptions()" (input)="editOptions.set($any($event.target).value)"
                            aria-label="Opções, uma por linha" data-testid="cf-edit-options"></textarea>
                }
                <div class="actions">
                  <button type="button" class="btn primary" [disabled]="busy() || !editLabel().trim()" (click)="save(f)"
                          data-testid="cf-save">Salvar</button>
                  <button type="button" class="btn" (click)="editingId.set(null)">Cancelar</button>
                </div>
              </div>
            } @else {
              <div>
                <strong data-testid="cf-label">{{ f.label }}</strong>
                <span class="badge">{{ typeLabels[f.type] }}</span>
                <p class="muted"><code>customFields.{{ f.key }}</code></p>
                @if (f.options.length) {
                  <p class="muted">{{ f.options.join(' · ') }}</p>
                }
              </div>
              <div class="actions">
                <button type="button" class="btn icon" [disabled]="busy() || first" (click)="move(f, -1)"
                        aria-label="Subir" data-testid="cf-up">↑</button>
                <button type="button" class="btn icon" [disabled]="busy() || last" (click)="move(f, 1)"
                        aria-label="Descer" data-testid="cf-down">↓</button>
                <button type="button" class="btn" (click)="edit(f)" data-testid="cf-edit">Editar</button>
                <button type="button" class="btn" (click)="askRemove(f)" data-testid="cf-remove">Remover</button>
              </div>
            }
            @if (removingId() === f.id) {
              <div class="notice" role="alertdialog" data-testid="cf-remove-confirm">
                @if (removeError(); as e) {
                  <p data-testid="cf-remove-error">{{ e }}</p>
                  <button type="button" class="btn" (click)="removingId.set(null)">Fechar</button>
                } @else {
                  <p><strong>Remover "{{ f.label }}"?</strong> Os valores já preenchidos nos itens deixam de aparecer.</p>
                  <button type="button" class="btn danger" [disabled]="busy()" (click)="remove(f)" data-testid="cf-remove-ok">Remover</button>
                  <button type="button" class="btn" (click)="removingId.set(null)">Cancelar</button>
                }
              </div>
            }
          </li>
        } @empty {
          <li class="muted" data-testid="cf-empty">Nenhum campo customizado neste tipo.</li>
        }
      </ul>
    </section>

    <section class="card" aria-label="Novo campo">
      <h2>Novo campo em {{ typeName() }}</h2>
      <div class="new">
        <label class="field">
          Rótulo
          <input [value]="newLabel()" (input)="setLabel($any($event.target).value)" maxlength="80" placeholder="ex.: Canal de venda"
                 data-testid="cf-new-label" />
        </label>
        <label class="field">
          Chave
          <input [value]="newKey()" (input)="setKey($any($event.target).value)" maxlength="40" data-testid="cf-new-key" />
        </label>
        <label class="field">
          Tipo
          <select [value]="newType()" (change)="newType.set($any($event.target).value)" data-testid="cf-new-type">
            @for (t of fieldTypes; track t) {
              <option [value]="t" [selected]="t === newType()">{{ typeLabels[t] }}</option>
            }
          </select>
        </label>
        @if (newType() === 'SELECT') {
          <label class="field wide">
            Opções (uma por linha ou separadas por vírgula)
            <textarea rows="3" [value]="newOptions()" (input)="newOptions.set($any($event.target).value)" data-testid="cf-new-options"></textarea>
          </label>
        }
      </div>
      <button type="button" class="btn primary" [disabled]="busy() || !newLabel().trim() || !newKey()" (click)="add()"
              data-testid="cf-add">Adicionar campo</button>
    </section>
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
    .field { display: grid; gap: 4px; font-size: 13px; font-weight: 600; max-width: 360px; }
    .field.wide { max-width: none; grid-column: 1 / -1; }
    .fields { list-style: none; margin: 12px 0 0; padding: 0; display: grid; gap: 8px; }
    .row { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; padding: 10px 12px; border: 1px solid var(--border); border-radius: 10px; }
    .row p { margin: 2px 0 0; }
    .badge { margin-left: 6px; padding: 1px 6px; border-radius: 999px; font-size: 11px; background: var(--surface-muted); color: var(--text-secondary); }
    .actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: flex-start; }
    .edit { display: grid; gap: 6px; flex: 1; max-width: 520px; }
    .new { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 10px; margin-bottom: 10px; }
    input, select, textarea { min-height: 32px; padding: 4px 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; font-weight: 400; background: var(--surface); color: var(--text); }
    .notice { flex-basis: 100%; padding: 10px 12px; border-radius: 8px; background: var(--notice-bg); color: var(--text); }
    .notice p { margin: 0 0 8px; }
    .notice .btn + .btn { margin-left: 6px; }
    .muted { color: var(--text-secondary); font-size: 13px; }
    .error { color: var(--danger); }
  `,
})
export class AdminCustomFields {
  private readonly api = inject(CustomFieldsService);
  private readonly work = inject(WorkService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly typeLabels = FIELD_TYPE_LABELS;
  protected readonly fieldTypes = Object.keys(FIELD_TYPE_LABELS) as CustomFieldType[];

  protected readonly types = signal<ArtifactType[]>([]);
  protected readonly typeKey = signal('');
  protected readonly typeName = computed(() => this.types().find((t) => t.key === this.typeKey())?.name ?? '');
  protected readonly fields = signal<CustomField[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly editingId = signal<string | null>(null);
  protected readonly editLabel = signal('');
  protected readonly editOptions = signal('');
  protected readonly removingId = signal<string | null>(null);
  protected readonly removeError = signal<string | null>(null);

  protected readonly newLabel = signal('');
  protected readonly newKey = signal('');
  protected readonly newType = signal<CustomFieldType>('TEXT');
  protected readonly newOptions = signal('');
  private keyTouched = false;

  constructor() {
    void this.init();
  }

  protected async chooseType(key: string): Promise<void> {
    this.typeKey.set(key);
    this.editingId.set(null);
    this.removingId.set(null);
    await this.load();
  }

  protected setLabel(label: string): void {
    this.newLabel.set(label);
    if (!this.keyTouched) {
      this.newKey.set(keyFromLabel(label));
    }
  }

  protected setKey(key: string): void {
    this.keyTouched = true;
    this.newKey.set(key.trim());
  }

  protected edit(f: CustomField): void {
    this.removingId.set(null);
    this.editingId.set(f.id);
    this.editLabel.set(f.label);
    this.editOptions.set(f.options.join('\n'));
  }

  protected askRemove(f: CustomField): void {
    this.editingId.set(null);
    this.removeError.set(null);
    this.removingId.set(f.id);
  }

  protected async add(): Promise<void> {
    await this.run(async () => {
      await this.api.create(this.typeKey(), {
        key: this.newKey(),
        label: this.newLabel(),
        type: this.newType(),
        options: parseOptions(this.newOptions()),
      });
      this.newLabel.set('');
      this.newKey.set('');
      this.newOptions.set('');
      this.keyTouched = false;
      await this.load();
      this.toast('Campo adicionado.');
    });
  }

  protected async save(f: CustomField): Promise<void> {
    await this.run(async () => {
      const change: { label: string; options?: string[] } = { label: this.editLabel().trim() };
      if (f.type === 'SELECT') {
        change.options = parseOptions(this.editOptions());
      }
      await this.api.update(f.id, change);
      this.editingId.set(null);
      await this.load();
      this.toast('Campo atualizado.');
    });
  }

  /** Troca a posição com o vizinho. */
  protected async move(f: CustomField, step: number): Promise<void> {
    const list = this.fields();
    const other = list[list.indexOf(f) + step];
    if (!other) {
      return;
    }
    await this.run(async () => {
      await this.api.update(f.id, { position: other.position });
      await this.api.update(other.id, { position: f.position });
      await this.load();
    });
  }

  protected async remove(f: CustomField): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.remove(f.id);
      this.removingId.set(null);
      await this.load();
      this.toast('Campo removido.');
    } catch (error) {
      // Campo citado por um gate (FIELD_IN_USE): a mensagem diz qual.
      this.removeError.set(messageOf(toApiError(error)));
    } finally {
      this.busy.set(false);
    }
  }

  private async init(): Promise<void> {
    try {
      const types = await this.work.artifactTypes();
      this.types.set(types);
      this.typeKey.set(types[0]?.key ?? '');
      await this.load();
    } catch (error) {
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async load(): Promise<void> {
    if (!this.typeKey()) {
      return;
    }
    try {
      this.fields.set(await this.api.list(this.typeKey()));
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
