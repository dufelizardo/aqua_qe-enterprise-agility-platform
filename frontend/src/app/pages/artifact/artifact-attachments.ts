import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';

import { messageOf, toApiError } from '../../core/api-error';
import { Attachment, AttachmentList, AttachmentsService, attachmentUrl, formatSize } from '../../knowledge/attachments.service';

/**
 * Anexos do artefato (UX/UI 7.4 e 7.6; #13, REV-08): nome, tipo, tamanho, quem enviou e quando. Quem lê baixa; quem
 * edita envia e remove, com confirmação. Acima do limite, o aviso aparece antes do envio.
 */
@Component({
  selector: 'app-artifact-attachments',
  imports: [DatePipe],
  template: `
    @if (data(); as d) {
      <div class="card attachments" data-testid="artifact-attachments">
        <header>
          <h2>Anexos</h2>
          @if (d.canEdit) {
            <label class="btn" [class.disabled]="busy()">
              Enviar arquivo
              <input type="file" class="visually-hidden" (change)="send($event)" [disabled]="busy()" data-testid="attachment-input" />
            </label>
          }
        </header>
        @if (error(); as e) {
          <p class="field-error" role="alert" data-testid="attachment-error">{{ e }}</p>
        }
        @if (d.items.length === 0) {
          <p class="muted" data-testid="attachment-empty">Nenhum anexo.</p>
        } @else {
          <ul class="list">
            @for (item of d.items; track item.id) {
              <li data-testid="attachment-row" [attr.data-name]="item.fileName">
                <a [href]="url(item.id)" [attr.download]="item.fileName" data-testid="attachment-download">{{ item.fileName }}</a>
                <span class="muted">
                  {{ size(item.size) }} · {{ item.contentType }} · {{ item.uploadedBy?.name ?? '—' }} · {{ item.uploadedAt | date: 'dd/MM/yyyy HH:mm' }}
                </span>
                @if (d.canEdit) {
                  @if (confirming() === item.id) {
                    <span class="confirm">
                      Remover?
                      <button type="button" class="btn danger" (click)="remove(item)" [disabled]="busy()" data-testid="attachment-remove-ok">Remover</button>
                      <button type="button" class="btn" (click)="confirming.set(null)">Cancelar</button>
                    </span>
                  } @else {
                    <button type="button" class="link" (click)="confirming.set(item.id)" data-testid="attachment-remove">Remover</button>
                  }
                }
              </li>
            }
          </ul>
        }
        <p class="hint">Até {{ size(d.maxSize) }} por arquivo.</p>
      </div>
    }
  `,
  styles: `
    .attachments header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
    .list { display: grid; gap: 6px; margin: 0; padding: 0; list-style: none; }
    .list li { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; }
    .confirm { display: inline-flex; align-items: center; gap: 6px; }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    label.btn { position: relative; cursor: pointer; }
    label.btn.disabled { opacity: 0.6; pointer-events: none; }
  `,
})
export class ArtifactAttachments {
  private readonly api = inject(AttachmentsService);

  /** Chave do artefato. */
  readonly artifact = input.required<string>();

  protected readonly data = signal<AttachmentList | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly confirming = signal<string | null>(null);

  constructor() {
    effect(() => {
      void this.load(this.artifact());
    });
  }

  protected url(id: string): string {
    return attachmentUrl(id);
  }

  protected size(bytes: number): string {
    return formatSize(bytes);
  }

  protected async send(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const current = this.data();
    if (!file || !current) {
      return;
    }
    this.error.set(null);
    if (file.size > current.maxSize) {
      this.error.set(`Arquivo acima do tamanho permitido (${formatSize(current.maxSize)}).`);
      return;
    }
    this.busy.set(true);
    try {
      await this.api.upload(this.artifact(), file);
      await this.load(this.artifact());
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(item: Attachment): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.api.remove(item.id);
      this.confirming.set(null);
      await this.load(this.artifact());
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    } finally {
      this.busy.set(false);
    }
  }

  private async load(key: string): Promise<void> {
    try {
      const list = await this.api.list(key);
      if (key === this.artifact()) {
        this.data.set(list);
      }
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    }
  }
}
