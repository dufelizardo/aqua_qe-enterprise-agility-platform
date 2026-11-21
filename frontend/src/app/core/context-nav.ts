import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { OrgLevel } from '../admin/organization.service';
import { LEVEL_SETUPS } from '../pages/level-board/level-setup';
import { FlightLevel, LevelsService } from './levels.service';
import { WorkspaceService } from './workspace.service';

/** Visões da unidade ativa e de qualquer unidade da árvore, para o menu e para a escolha na árvore. */
@Injectable({ providedIn: 'root' })
export class NavigationContext {
  private readonly http = inject(HttpClient);
  private readonly levels = inject(LevelsService);
  private readonly workspace = inject(WorkspaceService);
  private readonly typeNames = signal<Map<string, string>>(new Map());
  private loading: Promise<void> | null = null;

  /** Nível da unidade ativa (nome e Flight Level). */
  readonly currentLevel = computed(() => {
    const unit = this.workspace.current();
    return unit ? this.levels.levels().find((l) => l.key === unit.level) : undefined;
  });
  readonly views = computed(() => this.viewsFor(this.workspace.current()?.level ?? null));

  load(): Promise<void> {
    this.loading ??= firstValueFrom(this.http.get<{ items: { key: string; name: string }[] }>('/api/v1/artifact-types'))
      .then((r) => this.typeNames.set(new Map(r.items.map((t) => [t.key, t.name]))))
      .catch(() => {
        this.loading = null;
      });
    return this.loading;
  }

  viewsFor(levelKey: string | null): ContextView[] {
    const levels = this.levels.levels();
    const names = this.typeNames();
    return contextViews(
      levels.find((l) => l.key === levelKey),
      levels,
      (key) => names.get(key) ?? null,
    );
  }
}

/** Item do menu contextual: rota, parâmetros (aba do Squad) e rótulo. */
export interface ContextView {
  path: string;
  queryParams: Record<string, string> | null;
  label: string;
  testId: string;
}

const ORDER: FlightLevel[] = ['FL3', 'FL2', 'FL1'];
const ROUTE: Record<FlightLevel, { path: string; testId: string; screen: keyof typeof LEVEL_SETUPS }> = {
  FL3: { path: '/portfolio', testId: 'nav-portfolio', screen: 'portfolio' },
  FL2: { path: '/tactical', testId: 'nav-tactical', screen: 'tactical' },
  FL1: { path: '/squad', testId: 'nav-squad', screen: 'squad' },
};

/** Plural simples do português para rótulos de configuração ("Iniciativa" → "Iniciativas", "Ação" → "Ações"). */
export function plural(word: string): string {
  const w = word.trim();
  if (!w || /s$/i.test(w)) {
    return w;
  }
  if (/ão$/i.test(w)) {
    return w.replace(/ão$/i, 'ões');
  }
  if (/[rz]$/i.test(w)) {
    return `${w}es`;
  }
  // "Item" → "Itens"; "Stream" (-am) segue a regra geral.
  if (/[eiou]m$/i.test(w)) {
    return w.replace(/m$/i, 'ns');
  }
  return `${w}s`;
}

/**
 * Visões da unidade escolhida (bug #66; Proposta 06, Revisão 3): o trabalho do próprio nível e o consolidado dos níveis
 * abaixo dela, nunca os de cima, e o Wiki. Nada de nome fixo: o rótulo do próprio nível é o tipo principal do Flight
 * Level no plural (da configuração); os de baixo, o nome do nível na configuração com "abaixo". Funcionalidades que
 * ainda não existem não aparecem (UX/UI 4).
 *
 * @param unitLevel nível da unidade escolhida
 * @param levels níveis da instalação (GET /org-levels)
 * @param typeName nome do tipo de artefato pela chave (GET /artifact-types), ou nulo se ainda não carregou
 */
export function contextViews(
  unitLevel: OrgLevel | undefined,
  levels: OrgLevel[],
  typeName: (key: string) => string | null,
): ContextView[] {
  const own = unitLevel?.flightLevel ?? null;
  const levelName = (fl: FlightLevel) => levels.find((l) => l.flightLevel === fl)?.name ?? fl;
  const views: ContextView[] = [];
  if (own === 'FL1') {
    const squad = ROUTE.FL1;
    views.push(
      { path: squad.path, queryParams: null, label: 'Quadro', testId: squad.testId },
      { path: squad.path, queryParams: { tab: 'backlog' }, label: 'Backlog', testId: 'nav-squad-backlog' },
      { path: squad.path, queryParams: { tab: 'sprints' }, label: 'Sprints', testId: 'nav-squad-sprints' },
    );
  } else if (own) {
    const route = ROUTE[own];
    const principal = LEVEL_SETUPS[route.screen].creatableTypes[0];
    const name = (principal && typeName(principal)) || levelName(own);
    views.push({ path: route.path, queryParams: null, label: plural(name), testId: route.testId });
  }
  const below = own ? ORDER.slice(ORDER.indexOf(own) + 1) : ORDER;
  for (const fl of below) {
    const route = ROUTE[fl];
    views.push({ path: route.path, queryParams: null, label: `${plural(levelName(fl))} abaixo`, testId: route.testId });
  }
  // Conhecimento da unidade (Proposta 07, KNOW-013): vale para qualquer nível.
  views.push({ path: '/wiki', queryParams: null, label: 'Wiki', testId: 'nav-wiki' });
  return views;
}

/**
 * Para onde ir ao escolher uma unidade ({@code null} = recarregar a tela atual no novo contexto): no Wiki, continua no
 * Wiki; numa unidade com Flight Level, a primeira visão dela; sem Flight Level, a tela atual, porque o consolidado de
 * todos os níveis está no contexto dela.
 */
export function landingFor(views: ContextView[], currentPath: string, ownFlightLevel: string | null): ContextView | null {
  if (currentPath === '/wiki' || !ownFlightLevel) {
    return null;
  }
  return views[0] ?? null;
}
