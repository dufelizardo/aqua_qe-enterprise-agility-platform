import { Component, computed, effect, inject, output, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ContextView, NavigationContext, landingFor } from '../core/context-nav';
import { LevelsService } from '../core/levels.service';
import { NavNode, WorkspaceService } from '../core/workspace.service';

const EXPANDED_KEY = 'aquaqe.org-tree.expanded';

/**
 * Árvore organizacional do menu (Proposta 06, Revisões 2 e 3; PROPOSAL-07; bug #66): só unidades. Usa o mesmo serviço
 * de contexto do resto da aplicação ({@link WorkspaceService}): escolher uma unidade aqui é escolher o workspace e abrir a
 * página dela, com as abas no cabeçalho (`UnitHeader`). Ancestrais sem papel aparecem só para localização (ORG-D03); a
 * expansão fica lembrada no navegador; a busca filtra por nome.
 */
@Component({
  selector: 'app-org-tree',
  template: `
    <div class="org-tree" data-testid="org-tree-nav">
      <input
        class="search"
        type="search"
        placeholder="Buscar unidade"
        aria-label="Buscar unidade organizacional"
        [value]="term()"
        (input)="term.set($any($event.target).value)"
        data-testid="org-tree-search"
      />
      <ul role="tree" aria-label="Estrutura organizacional">
        @for (node of visible(); track node.id) {
          <li
            role="treeitem"
            [attr.aria-level]="node.depth + 1"
            [attr.aria-expanded]="expandable(node) && !searching() ? isOpen(node.id) : null"
            [attr.aria-selected]="node.id === workspace.current()?.id"
          >
            <div class="node" [style.padding-left.px]="4 + node.depth * 12">
              @if (expandable(node) && !searching()) {
                <button
                  type="button"
                  class="twisty"
                  (click)="toggle(node.id)"
                  [attr.aria-label]="(isOpen(node.id) ? 'Recolher ' : 'Expandir ') + node.name"
                  data-testid="org-node-toggle"
                >
                  {{ isOpen(node.id) ? '▾' : '▸' }}
                </button>
              } @else {
                <span class="twisty" aria-hidden="true"></span>
              }
              @if (node.selectable) {
                <button
                  type="button"
                  class="unit"
                  [class.current]="node.id === workspace.current()?.id"
                  [title]="levels.nameOfKey(node.level) + (node.flightLevel ? ' · ' + node.flightLevel : '')"
                  (click)="pick(node)"
                  (keydown)="keys($event, node)"
                  data-testid="org-node"
                >
                  {{ node.name }}
                </button>
              } @else {
                <span
                  class="unit ancestor"
                  [title]="levels.nameOfKey(node.level) + ': acima das suas unidades, só para localização'"
                  data-testid="org-node-ancestor"
                  >{{ node.name }}</span
                >
              }
            </div>
          </li>
        } @empty {
          <li class="empty">Nenhuma unidade.</li>
        }
      </ul>
    </div>
  `,
  styleUrl: './org-tree.scss',
})
export class OrgTree {
  protected readonly workspace = inject(WorkspaceService);
  protected readonly levels = inject(LevelsService);
  private readonly router = inject(Router);
  private readonly nav = inject(NavigationContext);

  /** Avisa o menu que uma unidade foi escolhida (no celular, a gaveta fecha). */
  readonly picked = output<void>();

  protected readonly term = signal('');
  protected readonly searching = computed(() => this.term().trim().length > 0);
  private readonly expanded = signal<Set<string>>(readExpanded());

  /** Nós visíveis: os de ramos abertos; na busca, os que casam e os ancestrais deles. */
  protected readonly visible = computed<NavNode[]>(() => {
    const nodes = this.workspace.navigation();
    const parentOf = new Map(nodes.map((n) => [n.id, n.parentId]));
    const term = this.term().trim().toLocaleLowerCase('pt-BR');
    if (term) {
      const keep = new Set<string>();
      for (const node of nodes) {
        if (node.name.toLocaleLowerCase('pt-BR').includes(term)) {
          for (let id: string | null | undefined = node.id; id; id = parentOf.get(id)) {
            keep.add(id);
          }
        }
      }
      return nodes.filter((n) => keep.has(n.id));
    }
    const open = this.expanded();
    return nodes.filter((n) => {
      for (let id = parentOf.get(n.id); id; id = parentOf.get(id)) {
        if (!open.has(id)) {
          return false;
        }
      }
      return true;
    });
  });

  constructor() {
    // Sem expansão lembrada, abre o caminho até a unidade ativa.
    effect(() => {
      const current = this.workspace.current();
      const nodes = this.workspace.navigation();
      if (!current || nodes.length === 0 || readStored() !== null) {
        return;
      }
      const parentOf = new Map(nodes.map((n) => [n.id, n.parentId]));
      const open = new Set<string>();
      for (let id = parentOf.get(current.id); id; id = parentOf.get(id)) {
        open.add(id);
      }
      open.add(current.id);
      this.expanded.set(open);
    });
  }

  protected expandable(node: NavNode): boolean {
    return node.hasChildren;
  }

  protected isOpen(id: string): boolean {
    return this.expanded().has(id);
  }

  protected toggle(id: string): void {
    const next = new Set(this.expanded());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.expanded.set(next);
    writeExpanded(next);
  }

  /**
   * Escolher a unidade é escolher o workspace e abrir a página dela (bug #66). A aba atual continua se existir na nova
   * unidade (com a aba da Squad, ?tab=); senão, abre a primeira ({@link landingFor}). A página aberta do Wiki é da
   * unidade anterior, então não acompanha.
   */
  protected pick(node: NavNode): void {
    const segments = this.router.parseUrl(this.router.url).root.children['primary']?.segments.map((s) => s.path) ?? [];
    const currentPath = '/' + segments.join('/');
    const landing = landingFor(this.nav.viewsFor(node.level), currentPath);
    const same = currentPath === '/wiki' ? '/wiki' : this.router.url;
    this.go(node, landing ? this.urlOf(landing) : same);
  }

  private go(node: NavNode, target: string): void {
    this.workspace.choose(node.id);
    // Abre o caminho até a unidade e a própria unidade: escolhida pela busca, ela continua visível ao limpar a busca.
    const parentOf = new Map(this.workspace.navigation().map((n) => [n.id, n.parentId]));
    const open = new Set(this.expanded());
    for (let id: string | null | undefined = node.id; id; id = parentOf.get(id)) {
      open.add(id);
    }
    this.expanded.set(open);
    writeExpanded(open);
    this.picked.emit();
    // Se outra navegação (um clique numa função) cancelar esta, não volta para o destino antigo por cima dela.
    void this.router
      .navigateByUrl('/workspace', { skipLocationChange: true })
      .then((done) => (done ? this.router.navigateByUrl(target) : false));
  }

  private urlOf(view: ContextView): string {
    return this.router.serializeUrl(this.router.createUrlTree([view.path], { queryParams: view.queryParams ?? {} }));
  }

  /** Setas direita e esquerda abrem e fecham o ramo (acessibilidade de árvore). */
  protected keys(event: KeyboardEvent, node: NavNode): void {
    if (!this.expandable(node) || this.searching()) {
      return;
    }
    if ((event.key === 'ArrowRight' && !this.isOpen(node.id)) || (event.key === 'ArrowLeft' && this.isOpen(node.id))) {
      event.preventDefault();
      this.toggle(node.id);
    }
  }
}

function readStored(): string | null {
  try {
    return localStorage.getItem(EXPANDED_KEY);
  } catch {
    return null;
  }
}

function readExpanded(): Set<string> {
  try {
    return new Set(JSON.parse(readStored() ?? '[]') as string[]);
  } catch {
    return new Set();
  }
}

function writeExpanded(ids: Set<string>): void {
  try {
    localStorage.setItem(EXPANDED_KEY, JSON.stringify([...ids]));
  } catch {
    // Sem armazenamento: a expansão vale só para esta página.
  }
}
