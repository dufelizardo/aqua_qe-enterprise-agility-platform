import { Component, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { OrgUnitDetail, OrganizationService } from '../admin/organization.service';
import { NavigationContext } from '../core/context-nav';
import { LevelsService } from '../core/levels.service';
import { WorkspaceService } from '../core/workspace.service';

/**
 * Cabeçalho da página da unidade ativa (bug #66, desenho do usuário em 2026-11-21): trilha até a raiz, nome e nível da
 * unidade e as **abas** da página. Toda unidade tem Wiki e Regimento; a Squad (FL1) também Quadro, Backlog e Sprints.
 * A árvore do menu tem só unidades; as funções ficam aqui, como abas.
 */
@Component({
  selector: 'app-unit-header',
  imports: [RouterLink, RouterLinkActive],
  template: `
    @if (workspace.current(); as unit) {
      <header class="unit-header" data-testid="unit-header">
        @if (context(); as detail) {
          <nav class="trail" aria-label="Caminho da unidade" data-testid="unit-trail">
            @for (ref of detail.path; track ref.id) {
              <span [title]="levels.nameOfKey(ref.level)">{{ ref.name }}</span>
              <span aria-hidden="true">›</span>
            }
            <strong>{{ detail.name }}</strong>
            <span class="level-tag"
              >{{ detail.level.name }}{{ detail.level.flightLevel ? ' · ' + detail.level.flightLevel : '' }}</span
            >
            @if (detail.links.serves.length > 0) {
              <span class="serves" data-testid="unit-serves">
                Atende a: {{ detail.links.serves.map((s) => s.name).join(', ') }}
              </span>
            }
          </nav>
        }
        <h1 data-testid="page-title">{{ unit.name }}</h1>
        <nav class="tabs" aria-label="Abas da unidade" data-testid="unit-tabs">
          @for (view of nav.views(); track view.testId) {
            <a
              [routerLink]="view.path"
              [queryParams]="view.queryParams"
              routerLinkActive="on"
              [routerLinkActiveOptions]="exact"
              ariaCurrentWhenActive="page"
              [attr.data-testid]="view.testId"
              >{{ view.label }}</a
            >
          }
        </nav>
      </header>
    }
  `,
  styles: `
    :host { display: block; }
    .trail { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; color: var(--text-muted); font-size: 13px; }
    .trail strong { color: var(--text); }
    .level-tag { padding: 1px 8px; border-radius: 999px; background: var(--blue-50); color: var(--blue-700); font-size: 11px; font-weight: 700; }
    .serves { margin-left: 8px; padding-left: 8px; border-left: 1px solid var(--border-strong); }
    h1 { margin: 4px 0 12px; font-size: 30px; font-weight: 800; letter-spacing: -0.02em; color: var(--text); }
    .tabs { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 20px; border-bottom: 1px solid var(--border-strong); }
    .tabs a { padding: 8px 14px; border-bottom: 3px solid transparent; color: var(--text-secondary); font-weight: 700; text-decoration: none; }
    .tabs a:hover { color: var(--text); }
    .tabs a.on { border-bottom-color: var(--amber-500); color: var(--text); }
    .tabs a:focus-visible { outline: 2px solid var(--blue-500); outline-offset: 1px; }
  `,
})
export class UnitHeader {
  protected readonly workspace = inject(WorkspaceService);
  protected readonly levels = inject(LevelsService);
  protected readonly nav = inject(NavigationContext);
  private readonly organization = inject(OrganizationService);

  protected readonly context = signal<OrgUnitDetail | null>(null);
  protected readonly exact = {
    paths: 'exact',
    queryParams: 'exact',
    matrixParams: 'ignored',
    fragment: 'ignored',
  } as const;

  constructor() {
    effect(() => {
      const id = this.workspace.current()?.id;
      untracked(() => {
        this.context.set(null);
        if (id) {
          this.organization
            .unit(id)
            .then((detail) => this.context.set(detail))
            .catch(() => this.context.set(null));
        }
      });
    });
  }
}
