import { Component, ElementRef, HostListener, computed, inject, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { SessionService } from '../core/session.service';
import { LevelsService } from '../core/levels.service';
import { WorkspaceService } from '../core/workspace.service';

/** Estrutura das telas internas: cabeçalho com busca, menu lateral por Flight Level e conteúdo (UX/UI, seção 4). */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule, MatMenuModule],
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
   * Menu por Flight Level (EXP-09), com o nome de cada nível vindo da configuração (Proposta 06, NAV-D1): uma
   * instalação que chame os níveis de "Produto", "Value Stream" e "Team" vê esses nomes.
   */
  protected readonly menu = computed(() => [
    { path: '/home', label: 'Home', testId: 'nav-home' },
    { path: '/portfolio', label: this.levels.nameOf('FL3'), testId: 'nav-portfolio' },
    { path: '/tactical', label: this.levels.nameOf('FL2'), testId: 'nav-tactical' },
    { path: '/squad', label: this.levels.nameOf('FL1'), testId: 'nav-squad' },
  ]);

  constructor() {
    void this.levels.load();
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

  protected chooseWorkspace(id: string): void {
    this.workspace.choose(id);
    // Recarrega a tela atual com o novo workspace.
    const url = this.router.url;
    void this.router.navigateByUrl('/workspace', { skipLocationChange: true }).then(() => this.router.navigateByUrl(url));
  }

  protected logout(): void {
    void this.session.logout();
  }
}
