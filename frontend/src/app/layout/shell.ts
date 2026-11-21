import { Component, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { NavigationContext } from '../core/context-nav';
import { SessionService } from '../core/session.service';
import { LevelsService } from '../core/levels.service';
import { WorkspaceService } from '../core/workspace.service';
import { OrgTree } from './org-tree';

/**
 * Estrutura das telas internas (UX/UI 4): cabeçalho com busca e a unidade ativa; menu lateral com Home, a árvore
 * organizacional com as funções da unidade escolhida dentro dela (Proposta 06, Revisão 3; bug #66) e Admin; conteúdo.
 * No celular, o menu é uma gaveta.
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

  /** As funções de cada unidade aparecem dentro da árvore, sob a unidade escolhida (bug #66; {@link OrgTree}). */
  private readonly nav = inject(NavigationContext);
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
