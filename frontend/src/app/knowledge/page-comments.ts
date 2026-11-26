import { DatePipe } from '@angular/common';
import { Component, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';

import { messageOf, toApiError } from '../core/api-error';
import {
  CommentPerson,
  CommentsService,
  PageComment,
  keptMentions,
  mentionQuery,
  mentionSegments,
} from './comments.service';

/**
 * Comentários da página (#85; Proposta 07, Fase D): lista com respostas de um nível, "Comentar trecho" (o texto
 * selecionado na página vira citação) e @menção com busca entre as pessoas que leem a página. Quem é mencionado ou
 * respondido recebe uma notificação.
 */
@Component({
  selector: 'app-page-comments',
  imports: [DatePipe],
  template: `
    <section class="comments" aria-label="Comentários" data-testid="page-comments">
      <h3>Comentários <span class="count" data-testid="comment-count">({{ visibleCount() }})</span></h3>

      @if (error()) {
        <p class="error" role="alert" data-testid="comment-error">{{ error() }}</p>
      }

      <ul>
        @for (c of comments(); track c.id) {
          <li [class.reply]="!!c.parentId" data-testid="comment-item">
            <div class="head">
              <strong>{{ c.author.name ?? 'Alguém' }}</strong>
              <span class="muted">{{ c.createdAt | date: 'dd/MM/yyyy HH:mm' }}</span>
            </div>
            @if (c.deleted) {
              <p class="muted" data-testid="comment-deleted">Comentário excluído.</p>
            } @else {
              @if (c.quote) {
                <blockquote data-testid="comment-quote">{{ c.quote }}</blockquote>
              }
              <p class="body" data-testid="comment-body">
                @for (s of segments(c); track $index) {
                  @if (s.mention) {<span class="mention">{{ s.text }}</span>} @else {<span>{{ s.text }}</span>}
                }
              </p>
              <div class="actions">
                <button type="button" class="link" (click)="reply(c)" data-testid="comment-reply">Responder</button>
                @if (c.canDelete) {
                  <button type="button" class="link" (click)="remove(c)" data-testid="comment-delete">Excluir</button>
                }
              </div>
            }
          </li>
        } @empty {
          <li class="muted empty" data-testid="comment-empty">Nenhum comentário ainda.</li>
        }
      </ul>

      <form class="compose" (submit)="send($event)">
        @if (replyTo(); as r) {
          <p class="context" data-testid="comment-replying">
            Respondendo a {{ r.author.name ?? 'Alguém' }}
            <button type="button" class="link" (click)="replyTo.set(null)">Cancelar resposta</button>
          </p>
        }
        @if (quote(); as q) {
          <div class="context quote-preview" data-testid="comment-quote-preview">
            <blockquote>{{ q }}</blockquote>
            <button type="button" class="link" (click)="quote.set(null)" data-testid="comment-quote-clear">Tirar o trecho</button>
          </div>
        }
        <div class="field">
          <textarea
            #input
            rows="3"
            maxlength="2000"
            placeholder="Escreva um comentário. Use @ para mencionar alguém."
            aria-label="Novo comentário"
            [value]="body()"
            (input)="typed()"
            (click)="typed()"
            (keydown)="keydown($event)"
            data-testid="comment-input"
          ></textarea>
          @if (suggestions().length > 0) {
            <ul class="suggestions" role="listbox" aria-label="Pessoas para mencionar">
              @for (p of suggestions(); track p.id; let i = $index) {
                <li role="option" [attr.aria-selected]="i === active()">
                  <button type="button" [class.on]="i === active()" (mousedown)="$event.preventDefault()" (click)="choose(p)" data-testid="comment-mention-option">{{ p.name }}</button>
                </li>
              }
            </ul>
          }
        </div>
        <button type="submit" class="btn primary" [disabled]="busy() || !body().trim()" data-testid="comment-send">Comentar</button>
      </form>
    </section>
  `,
  styles: `
    .comments { margin-top: 32px; border-top: 1px solid var(--border); padding-top: 16px; }
    h3 { font-size: 16px; margin: 0 0 12px; color: var(--text); }
    .count { color: var(--text-secondary); font-weight: 400; }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
    li.reply { margin-left: 28px; padding-left: 12px; border-left: 2px solid var(--border); }
    .head { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
    .muted { color: var(--text-secondary); font-size: 13px; }
    .body { margin: 4px 0; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--text); }
    .mention { color: var(--primary, inherit); font-weight: 600; }
    blockquote { margin: 4px 0; padding: 4px 10px; border-left: 3px solid var(--border); color: var(--text-secondary); font-style: italic; overflow-wrap: anywhere; }
    .actions { display: flex; gap: 12px; }
    .link { background: none; border: none; padding: 0; color: var(--primary, inherit); cursor: pointer; font: inherit; font-size: 13px; }
    .compose { margin-top: 16px; display: grid; gap: 8px; }
    .context { margin: 0; font-size: 13px; color: var(--text-secondary); }
    .field { position: relative; }
    textarea { width: 100%; box-sizing: border-box; font: inherit; padding: 8px 10px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); color: var(--text); resize: vertical; }
    .suggestions { position: absolute; z-index: 5; left: 0; top: 100%; min-width: 220px; max-width: 100%; margin-top: 4px; padding: 4px; gap: 0; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); box-shadow: 0 6px 18px rgba(0,0,0,.12); }
    .suggestions button { display: block; width: 100%; text-align: left; padding: 6px 10px; border: none; border-radius: 6px; background: none; color: var(--text); cursor: pointer; font: inherit; }
    .suggestions button.on, .suggestions button:hover { background: var(--hover, rgba(0,0,0,.06)); }
    .btn { justify-self: start; }
    .error { color: var(--danger, #b42318); }
    .empty { font-size: 14px; }
  `,
})
export class PageComments {
  readonly pageId = input.required<string>();

  private readonly service = inject(CommentsService);
  private readonly input = viewChild<ElementRef<HTMLTextAreaElement>>('input');

  protected readonly comments = signal<PageComment[]>([]);
  protected readonly visibleCount = computed(() => this.comments().filter((c) => !c.deleted).length);
  protected readonly body = signal('');
  readonly quote = signal<string | null>(null);
  protected readonly replyTo = signal<PageComment | null>(null);
  protected readonly suggestions = signal<CommentPerson[]>([]);
  protected readonly active = signal(0);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private chosen: CommentPerson[] = [];
  private mentionStart = -1;
  private searchSeq = 0;

  constructor() {
    effect(() => {
      const id = this.pageId();
      untracked(() => {
        this.reset();
        void this.load(id);
      });
    });
  }

  /** "Comentar trecho": o texto selecionado na página vira a citação do novo comentário. */
  quoteText(text: string): void {
    this.quote.set(text.length > 500 ? `${text.slice(0, 499)}…` : text);
    this.input()?.nativeElement.focus();
  }

  protected segments(c: PageComment) {
    return mentionSegments(c.body, c.mentions);
  }

  protected reply(c: PageComment): void {
    this.replyTo.set(c);
    this.input()?.nativeElement.focus();
  }

  protected async remove(c: PageComment): Promise<void> {
    if (!window.confirm('Excluir este comentário?')) {
      return;
    }
    try {
      await this.service.remove(this.pageId(), c.id);
      await this.load(this.pageId());
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    }
  }

  protected typed(): void {
    const el = this.input()?.nativeElement;
    if (!el) {
      return;
    }
    this.body.set(el.value);
    const open = mentionQuery(el.value, el.selectionStart ?? el.value.length);
    if (!open) {
      this.closeSuggestions();
      return;
    }
    this.mentionStart = open.start;
    void this.search(open.query);
  }

  protected keydown(event: KeyboardEvent): void {
    const list = this.suggestions();
    if (list.length === 0) {
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      this.active.set((this.active() + step + list.length) % list.length);
    } else if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault();
      this.choose(list[this.active()]!);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.closeSuggestions();
    }
  }

  protected choose(person: CommentPerson): void {
    const el = this.input()?.nativeElement;
    if (!el || this.mentionStart < 0 || !person.name) {
      return;
    }
    const cursor = el.selectionStart ?? el.value.length;
    const text = `${el.value.slice(0, this.mentionStart)}@${person.name} ${el.value.slice(cursor)}`;
    el.value = text;
    const position = this.mentionStart + person.name.length + 2;
    el.setSelectionRange(position, position);
    el.focus();
    this.body.set(text);
    if (!this.chosen.some((p) => p.id === person.id)) {
      this.chosen.push(person);
    }
    this.closeSuggestions();
  }

  protected async send(event: Event): Promise<void> {
    event.preventDefault();
    const text = this.body().trim();
    if (!text || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.service.create(this.pageId(), {
        body: text,
        quote: this.quote(),
        parentId: this.replyTo()?.id ?? null,
        mentionIds: keptMentions(text, this.chosen),
      });
      this.reset();
      await this.load(this.pageId());
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    } finally {
      this.busy.set(false);
    }
  }

  private async search(query: string): Promise<void> {
    const seq = ++this.searchSeq;
    try {
      const people = await this.service.people(this.pageId(), query);
      if (seq === this.searchSeq) {
        this.suggestions.set(people.slice(0, 8));
        this.active.set(0);
      }
    } catch {
      this.closeSuggestions();
    }
  }

  private closeSuggestions(): void {
    this.searchSeq++;
    this.suggestions.set([]);
    this.mentionStart = -1;
  }

  private reset(): void {
    this.body.set('');
    const el = this.input()?.nativeElement;
    if (el) {
      el.value = '';
    }
    this.quote.set(null);
    this.replyTo.set(null);
    this.chosen = [];
    this.error.set(null);
    this.closeSuggestions();
  }

  private async load(id: string): Promise<void> {
    try {
      const list = await this.service.list(id);
      if (id === this.pageId()) {
        this.comments.set(list);
      }
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    }
  }
}
