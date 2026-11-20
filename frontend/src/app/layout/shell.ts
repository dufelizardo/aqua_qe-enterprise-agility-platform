import { Component, ElementRef, HostListener, inject, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { SessionService } from '../core/session.service';
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
  private readonly router = inject(Router);
  private readonly searchInput = viewChild.required<ElementRef<HTMLInputElement>>('searchInput');

  protected readonly menu = [
    { path: '/home', label: 'Home', testId: 'nav-home' },
    { path: '/portfolio', label: 'Portfolio', testId: 'nav-portfolio' },
    { path: '/tactical', label: 'Tactical', testId: 'nav-tactical' },
    { path: '/squad', label: 'Squad', testId: 'nav-squad' },
  ];

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
