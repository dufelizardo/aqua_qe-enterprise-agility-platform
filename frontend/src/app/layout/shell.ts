import { Component, ElementRef, HostListener, computed, inject, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { NavigationContext } from '../core/context-nav';
import { SessionService } from '../core/session.service';
import { LevelsService } from '../core/levels.service';
import { WorkspaceService } from '../core/workspace.service';
import { OrgTree } from './org-tree';

/**
 * Estrutura das telas internas (UX/UI 4): cabeçalho com busca e a unidade ativa; menu lateral com a árvore organizacional
 * e as visões por Flight Level da unidade escolhida (Proposta 06, Revisão 2); conteúdo. No celular, o menu é uma gaveta.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatMenuModule, OrgTree],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly session = inject(SessionService);
  protected readonly workspace = inject(WorkspaceService);
  protected readonly levels = inject(LevelsService);
  private readonly router = inject(Router);
  private readonly searchInput = viewChild.required<ElementRef<HTMLInputElement>>('searchInput');

  /**
   * Visões da unidade escolhida (bug #66; Proposta 06, Revisão 3): o trabalho do próprio nível, o consolidado dos níveis
   * abaixo e o Wiki, com nomes vindos da configuração ({@link contextViews}).
   */
  protected readonly nav = inject(NavigationContext);
  protected readonly views = this.nav.views;
  /** Título da seção: onde o usuário está ("Canais · Cadeia"). */
  protected readonly contextTitle = computed(() => {
    const unit = this.workspace.current();
    const level = this.nav.currentLevel();
    return unit ? `${unit.name}${level ? ' · ' + level.name : ''}` : 'Visões da unidade';
  });
  protected readonly exactWithQuery = {
    paths: 'exact',
    queryParams: 'exact',
    matrixParams: 'ignored',
    fragment: 'ignored',
  } as const;
  /** Gaveta do menu no celular. */
  protected readonly drawer = signal(false);

  constructor() {
    void this.levels.load();
    void this.nav.load();
    if (!this.workspace.loaded()) {
      void this.workspace.load();
    }
  }

  /** Atalho "/" foca a busca (UX/UI, seção 4), exceto quando o usuário já está digitando. */
  @HostListener('document:keydown', ['$event'])
  protected focusSearch(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    const typing = target?.closest('input, textarea, select, [contenteditable="true"]');
    if (event.key === '/' && !typing) {
      event.preventDefault();
      this.searchInput().nativeElement.focus();
    }
  }

  protected search(term: string): void {
    const q = term.trim();
    if (q) {
      void this.router.navigate(['/busca'], { queryParams: { q } });
    }
  }

  protected switchPersona(username: string): void {
    // Os papéis mudam com a persona; os workspaces também.
    void this.session.switchPersona(username).then(() => this.workspace.load());
  }



  protected logout(): void {
    void this.session.logout();
  }
}
