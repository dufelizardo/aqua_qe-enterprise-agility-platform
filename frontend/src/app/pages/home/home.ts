import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ActivityService, ItemLink, NewsItem, RecentItem, actionLabel, actorLabel, itemLabel, itemLink } from '../../core/activity.service';
import { SessionService } from '../../core/session.service';
import { WorkspaceService } from '../../core/workspace.service';

/**
 * Home — Meu trabalho (UX/UI, seção 7.2). Grade de cartões: "Continuar de onde parou" (o que a pessoa abriu por
 * último) e "Novidades" (o que mudou nas unidades que ela lê), #83. Aprovações e itens sob responsabilidade entram com
 * os módulos correspondentes.
 */
@Component({
  selector: 'app-home',
  imports: [DatePipe, RouterLink],
  template: `
    <h1 data-testid="page-title">Meu trabalho</h1>
    <p data-testid="welcome">Olá, {{ session.displayName() }}.</p>
    <section class="widgets" aria-label="Meu trabalho">
      <article class="card" data-testid="home-recent">
        <h2>Continuar de onde parou</h2>
        @if (recent() === null) {
          <p class="muted">Carregando…</p>
        } @else if (recent()!.length === 0) {
          <p class="muted" data-testid="home-recent-empty">Os artefatos, documentos e páginas que você abrir aparecem aqui.</p>
        } @else {
          <ul>
            @for (item of recent(); track item.type + item.ref) {
              @let to = link(item);
              <li data-testid="home-recent-item">
                <a [routerLink]="to.path" [queryParams]="to.queryParams" (click)="prepare(to)">{{ label(item) }}</a>
                <span class="meta">{{ item.orgUnit?.name }} · {{ item.visitedAt | date: 'dd/MM HH:mm' }}</span>
              </li>
            }
          </ul>
        }
      </article>
      <article class="card" data-testid="home-news">
        <h2>Novidades</h2>
        @if (news() === null) {
          <p class="muted">Carregando…</p>
        } @else if (news()!.length === 0) {
          <p class="muted" data-testid="home-news-empty">Nada mudou nos últimos 30 dias nas unidades que você acompanha.</p>
        } @else {
          <ul>
            @for (item of news(); track item.type + item.ref) {
              @let to = link(item);
              <li data-testid="home-news-item">
                <span>
                  <strong>{{ actor(item) }}</strong> {{ action(item.action) }}
                  <a [routerLink]="to.path" [queryParams]="to.queryParams" (click)="prepare(to)">{{ label(item) }}</a>
                </span>
                <span class="meta">{{ item.orgUnit?.name }} · {{ item.occurredAt | date: 'dd/MM HH:mm' }}</span>
              </li>
            }
          </ul>
        }
      </article>
    </section>
    @if (error()) {
      <p class="error" role="alert" data-testid="home-error">{{ error() }}</p>
    }
  `,
  styles: `
    h1 { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 4px; color: var(--text); }
    h2 { font-size: 16px; font-weight: 700; margin: 0 0 12px; color: var(--text); }
    p { color: var(--text-secondary); margin: 0; }
    .widgets { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 1fr)); gap: 16px; margin-top: 24px; }
    .card { padding: 20px 24px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); min-width: 0; }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
    li { display: grid; gap: 2px; min-width: 0; color: var(--text); }
    a { color: var(--primary, inherit); font-weight: 600; text-decoration: none; overflow-wrap: anywhere; }
    a:hover { text-decoration: underline; }
    .meta { font-size: 12px; color: var(--text-secondary); }
    .muted { font-size: 14px; }
    .error { margin-top: 16px; color: var(--danger, #b42318); }
  `,
})
export class Home {
  protected readonly session = inject(SessionService);
  private readonly activity = inject(ActivityService);
  private readonly workspace = inject(WorkspaceService);

  protected readonly recent = signal<RecentItem[] | null>(null);
  protected readonly news = signal<NewsItem[] | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly label = itemLabel;
  protected readonly link = itemLink;
  protected readonly action = actionLabel;
  protected readonly actor = actorLabel;

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    const [recent, news] = await Promise.allSettled([this.activity.recent(), this.activity.news()]);
    this.recent.set(recent.status === 'fulfilled' ? recent.value : []);
    this.news.set(news.status === 'fulfilled' ? news.value : []);
    if (recent.status === 'rejected' || news.status === 'rejected') {
      this.error.set('Não foi possível carregar tudo agora. Recarregue a página para tentar de novo.');
    }
  }

  /** A página do Wiki abre na unidade dela: ativa a unidade antes de navegar, quando a pessoa pode escolhê-la. */
  protected prepare(to: ItemLink): void {
    if (to.unitId && this.workspace.workspaces().some((w) => w.id === to.unitId)) {
      this.workspace.choose(to.unitId);
    }
  }
}
