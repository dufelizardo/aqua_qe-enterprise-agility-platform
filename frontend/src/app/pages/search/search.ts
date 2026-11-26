import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ItemLink } from '../../core/activity.service';
import { messageOf, toApiError } from '../../core/api-error';
import { HitKind, SearchHit, SearchResults, SearchService, highlight, hitBadge, hitLink } from '../../core/search.service';
import { WorkspaceService } from '../../core/workspace.service';

type Filter = 'ALL' | HitKind;

/**
 * Resultados de busca (UX/UI 7.9; #84): artefatos, documentos do artefato e páginas do Wiki que a pessoa lê, separados
 * por tipo, com o trecho encontrado e os termos marcados. A busca vem da caixa do topo (`/busca?q=`).
 */
@Component({
  selector: 'app-search',
  imports: [RouterLink],
  template: `
    <h1 data-testid="page-title">Busca</h1>
    @if (q()) {
      <p data-testid="search-term">Termo pesquisado: <strong>{{ q() }}</strong></p>
    } @else {
      <p data-testid="search-hint">Digite na caixa "Buscar" do topo. Atalho: tecla /.</p>
    }

    @if (error()) {
      <p class="error" role="alert" data-testid="search-error">{{ error() }}</p>
    } @else if (loading()) {
      <p data-testid="search-loading">Buscando…</p>
    } @else if (results(); as r) {
      @if (total() === 0) {
        <p class="empty" data-testid="search-empty">
          Nada encontrado para <em>{{ r.q }}</em>. A busca só inclui itens a que você tem acesso.
        </p>
      } @else {
        <div class="filters" role="tablist" aria-label="Tipo de resultado">
          @for (f of filters; track f.value) {
            <button type="button" role="tab" [class.on]="filter() === f.value" [attr.aria-selected]="filter() === f.value"
              (click)="filter.set(f.value)" [attr.data-testid]="'search-filter-' + f.value">
              {{ f.label }} ({{ count(f.value) }})
            </button>
          }
        </div>
        @for (group of groups(); track group.kind) {
          @if (group.items.length > 0) {
            <section [attr.data-testid]="'search-group-' + group.kind">
              <h2>{{ group.label }}</h2>
              <ul>
                @for (hit of group.items; track $index) {
                  @let to = link(hit);
                  <li data-testid="search-result">
                    <div class="line">
                      <span class="badge">{{ badge(hit) }}</span>
                      <a [routerLink]="to.path" [queryParams]="to.queryParams" (click)="prepare(to)" data-testid="search-result-link">
                        @for (s of marks(hit.title); track $index) {
                          @if (s.mark) {<mark>{{ s.text }}</mark>} @else {<span>{{ s.text }}</span>}
                        }
                      </a>
                    </div>
                    @if (hit.snippet) {
                      <p class="snippet" data-testid="search-snippet">
                        @for (s of marks(hit.snippet); track $index) {
                          @if (s.mark) {<mark>{{ s.text }}</mark>} @else {<span>{{ s.text }}</span>}
                        }
                      </p>
                    }
                    <span class="meta">{{ meta(hit) }}</span>
                  </li>
                }
              </ul>
            </section>
          }
        }
      }
    }
  `,
  styles: `
    h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 4px; color: var(--text); }
    h2 { font-size: 16px; font-weight: 700; margin: 24px 0 8px; color: var(--text); }
    p { color: var(--text-secondary); margin: 0 0 4px; }
    .filters { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 20px; }
    .filters button { border: 1px solid var(--border); background: var(--surface); color: var(--text); border-radius: 999px; padding: 6px 12px; cursor: pointer; font: inherit; font-size: 13px; }
    .filters button.on { border-color: var(--primary, currentColor); font-weight: 700; }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
    li { padding: 12px 16px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); min-width: 0; display: grid; gap: 4px; }
    .line { display: flex; gap: 8px; align-items: baseline; min-width: 0; }
    .badge { flex: none; font-size: 12px; font-weight: 700; color: var(--text-secondary); }
    a { color: var(--primary, inherit); font-weight: 600; text-decoration: none; overflow-wrap: anywhere; }
    a:hover { text-decoration: underline; }
    .snippet { font-size: 14px; color: var(--text); overflow-wrap: anywhere; margin: 0; }
    mark { background: color-mix(in srgb, #f5c400 40%, transparent); color: inherit; border-radius: 2px; }
    .meta { font-size: 12px; color: var(--text-secondary); }
    .error { color: var(--danger, #b42318); margin-top: 16px; }
    .empty { margin-top: 16px; }
  `,
})
export class Search {
  readonly q = input('');

  private readonly service = inject(SearchService);
  private readonly workspace = inject(WorkspaceService);
  private readonly dates = new DatePipe('en-US');

  protected readonly results = signal<SearchResults | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal<Filter>('ALL');
  private seq = 0;

  protected readonly filters: { value: Filter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'ARTIFACT', label: 'Artefatos' },
    { value: 'DOCUMENT', label: 'Documentos' },
    { value: 'PAGE', label: 'Páginas' },
  ];

  protected readonly total = computed(() => this.count('ALL'));
  protected readonly groups = computed(() => {
    const r = this.results();
    const f = this.filter();
    if (!r) {
      return [];
    }
    return [
      { kind: 'ARTIFACT' as const, label: 'Artefatos', items: r.artifacts },
      { kind: 'DOCUMENT' as const, label: 'Documentos', items: r.documents },
      { kind: 'PAGE' as const, label: 'Páginas', items: r.pages },
    ].filter((g) => f === 'ALL' || f === g.kind);
  });

  protected readonly badge = hitBadge;
  protected readonly link = hitLink;

  constructor() {
    effect(() => {
      const q = this.q().trim();
      untracked(() => void this.run(q));
    });
  }

  protected count(f: Filter): number {
    const r = this.results();
    if (!r) {
      return 0;
    }
    const counts = { ARTIFACT: r.artifacts.length, DOCUMENT: r.documents.length, PAGE: r.pages.length };
    return f === 'ALL' ? counts.ARTIFACT + counts.DOCUMENT + counts.PAGE : counts[f];
  }

  protected meta(hit: SearchHit): string {
    const date = hit.updatedAt ? this.dates.transform(hit.updatedAt, 'dd/MM/yyyy') : null;
    return [hit.orgUnit?.name, hit.status, date].filter((p) => !!p).join(' · ');
  }

  protected marks(text: string | null) {
    return highlight(text, this.q());
  }

  /** A página do Wiki abre na unidade dela: ativa a unidade antes de navegar, quando a pessoa pode escolhê-la. */
  protected prepare(to: ItemLink): void {
    if (to.unitId && this.workspace.workspaces().some((w) => w.id === to.unitId)) {
      this.workspace.choose(to.unitId);
    }
  }

  private async run(q: string): Promise<void> {
    const seq = ++this.seq;
    this.error.set(null);
    this.results.set(null);
    this.filter.set('ALL');
    if (!q) {
      return;
    }
    if (q.length < 2) {
      this.error.set('Digite pelo menos 2 caracteres.');
      return;
    }
    this.loading.set(true);
    try {
      const results = await this.service.search(q);
      if (seq === this.seq) {
        this.results.set(results);
      }
    } catch (error) {
      if (seq === this.seq) {
        this.error.set(messageOf(toApiError(error)));
      }
    } finally {
      if (seq === this.seq) {
        this.loading.set(false);
      }
    }
  }
}

