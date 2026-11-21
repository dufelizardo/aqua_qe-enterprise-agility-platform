import { Injectable, computed, inject } from '@angular/core';

import { OrgLevel } from '../admin/organization.service';
import { LevelsService } from './levels.service';
import { WorkspaceService } from './workspace.service';

/** Abas da página da unidade ativa e de qualquer unidade, para o cabeçalho da unidade e para a escolha na árvore. */
@Injectable({ providedIn: 'root' })
export class NavigationContext {
  private readonly levels = inject(LevelsService);
  private readonly workspace = inject(WorkspaceService);

  /** Abas da unidade ativa. */
  readonly views = computed(() => this.viewsFor(this.workspace.current()?.level ?? null));

  viewsFor(levelKey: string | null): ContextView[] {
    return contextViews(this.levels.levels().find((l) => l.key === levelKey));
  }
}

/** Aba da página da unidade: rota, parâmetros (aba da Squad) e rótulo. */
export interface ContextView {
  path: string;
  queryParams: Record<string, string> | null;
  label: string;
  testId: string;
}

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
 * Abas da página de uma unidade (bug #66, desenho do usuário em 2026-11-21): toda unidade tem o próprio **Wiki** e o
 * próprio **Regimento**, independentes; a unidade de FL1 (Squad) tem também **Quadro**, **Backlog** e **Sprints**, onde
 * nasce todo o trabalho (Iniciativas, Features e Histórias). A árvore do menu tem só unidades. Nenhum nome de nível fica
 * no código: o que decide é o Flight Level da configuração.
 */
export function contextViews(unitLevel: OrgLevel | undefined): ContextView[] {
  const views: ContextView[] = [
    { path: '/wiki', queryParams: null, label: 'Wiki', testId: 'nav-wiki' },
    { path: '/regimento', queryParams: null, label: 'Regimento', testId: 'nav-regimento' },
  ];
  if (unitLevel?.flightLevel === 'FL1') {
    views.push(
      { path: '/squad', queryParams: null, label: 'Quadro', testId: 'nav-squad' },
      { path: '/squad', queryParams: { tab: 'backlog' }, label: 'Backlog', testId: 'nav-squad-backlog' },
      { path: '/squad', queryParams: { tab: 'sprints' }, label: 'Sprints', testId: 'nav-squad-sprints' },
    );
  }
  return views;
}

/**
 * Para onde ir ao escolher uma unidade na árvore ({@code null} = recarregar a aba atual na nova unidade): clicar numa
 * unidade abre a página dela; continua na mesma aba se ela existir na nova unidade, senão abre a primeira (o Wiki).
 */
export function landingFor(views: ContextView[], currentPath: string): ContextView | null {
  if (views.some((v) => v.path === currentPath)) {
    return null;
  }
  return views[0] ?? null;
}
