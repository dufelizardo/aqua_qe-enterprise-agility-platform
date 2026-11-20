import { CdkDrag, CdkDragDrop, CdkDragStart, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { Component, computed, inject, signal } from '@angular/core';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { OrgUnit, OrganizationService } from '../../admin/organization.service';
import { ApiError, fieldError, messageOf, toApiError } from '../../core/api-error';
import { WorkspaceService } from '../../core/workspace.service';
import {
  Artifact,
  Board,
  BoardColumn,
  BoardView,
  Card,
  TransitionOption,
  WorkService,
  transitionInto,
} from '../../work/work.service';

/**
 * Portfolio Management (UX/UI 7.3): as Iniciativas do workspace no fluxo de FL3, em quadro ou lista. Mover um cartão é
 * executar a transição (UX/UI 6.2): o cartão só muda de coluna depois da resposta de sucesso.
 */
@Component({
  selector: 'app-portfolio',
  imports: [CdkDropListGroup, CdkDropList, CdkDrag, MatMenuModule, MatSnackBarModule],
  templateUrl: './portfolio.html',
  styleUrl: './portfolio.scss',
})
export class Portfolio {
  private readonly work = inject(WorkService);
  private readonly organization = inject(OrganizationService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly workspace = inject(WorkspaceService);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly board = signal<Board | null>(null);
  protected readonly view = signal<BoardView | null>(null);
  protected readonly mode = signal<'quadro' | 'lista'>('quadro');
  protected readonly filter = signal('');
  protected readonly list = signal<Artifact[]>([]);
  protected readonly busy = signal(false);

  /** Colunas para onde o cartão arrastado pode ir; as demais ficam esmaecidas (UX/UI 6.2). */
  protected readonly dragOptions = signal<TransitionOption[] | null>(null);
  protected readonly menuOptions = signal<TransitionOption[] | null>(null);

  // Nova Iniciativa
  protected readonly creating = signal(false);
  protected readonly newTitle = signal('');
  protected readonly newDescription = signal('');
  protected readonly newUnit = signal('');
  protected readonly createError = signal<ApiError | null>(null);
  protected readonly portfolioUnits = signal<OrgUnit[]>([]);

  /** Só decide o que mostrar; quem decide o acesso é a API (SSD 2). */
  protected readonly canCreate = computed(() =>
    this.workspace.hasRole('PLATFORM_ADMIN', 'BUSINESS_OWNER', 'PRODUCT_MANAGER'),
  );
  protected readonly canManageBoards = computed(() =>
    this.workspace.hasRole('PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD'),
  );

  protected readonly upstream = computed(() => this.filtered().filter((c) => c.phase === 'UPSTREAM'));
  protected readonly downstream = computed(() => this.filtered().filter((c) => c.phase === 'DOWNSTREAM'));
  protected readonly filteredList = computed(() => this.list().filter((a) => this.matches(a.key, a.title, a.owner?.name)));

  private readonly filtered = computed<BoardColumn[]>(() =>
    (this.view()?.columns ?? []).map((column) => ({
      ...column,
      cards: column.cards.filter((card) => this.matches(card.key, card.title, card.owner?.name)),
    })),
  );

  protected readonly fieldError = fieldError;
  protected readonly messageOf = messageOf;

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    const workspaceId = this.workspace.currentWorkspaceId();
    if (!workspaceId) {
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);
    try {
      const boards = await this.work.boards(workspaceId, 'FL3');
      this.board.set(boards[0] ?? null);
      await this.refresh();
    } catch (error) {
      this.loadError.set(messageOf(toApiError(error)));
    } finally {
      this.loading.set(false);
    }
  }

  protected async refresh(): Promise<void> {
    const board = this.board();
    const [view, list] = await Promise.all([
      board ? this.work.boardView(board.id) : Promise.resolve(null),
      this.work.artifacts('FL3'),
    ]);
    this.view.set(view);
    this.list.set(list);
  }

  protected async createBoard(): Promise<void> {
    const workspaceId = this.workspace.currentWorkspaceId();
    if (!workspaceId) {
      return;
    }
    await this.run(async () => {
      this.board.set(await this.work.createBoard(workspaceId, 'FL3'));
      await this.refresh();
      this.toast('Quadro de portfólio criado.');
    });
  }

  // ---------------------------------------------------------------------------------------- Nova Iniciativa

  protected async openCreate(): Promise<void> {
    this.creating.set(true);
    this.createError.set(null);
    const units = await this.organization.allUnits();
    const options = unitsInWorkspace(units, this.workspace.currentWorkspaceId()).filter(
      (unit) => unit.active && unit.level.flightLevel === 'FL3',
    );
    this.portfolioUnits.set(options);
    this.newUnit.set(options[0]?.id ?? '');
  }

  protected async createInitiative(): Promise<void> {
    this.createError.set(null);
    this.busy.set(true);
    try {
      const created = await this.work.createArtifact({
        type: 'INITIATIVE',
        title: this.newTitle(),
        description: this.newDescription() || undefined,
        orgUnitId: this.newUnit(),
      });
      this.creating.set(false);
      this.newTitle.set('');
      this.newDescription.set('');
      await this.refresh();
      this.toast(`${created.key} criada.`);
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.code === 'VALIDATION_FAILED' || apiError.status === 422) {
        this.createError.set(apiError);
      } else {
        this.toast(messageOf(apiError));
      }
    } finally {
      this.busy.set(false);
    }
  }

  // ---------------------------------------------------------------------------------------- mover

  protected async onDragStart(event: CdkDragStart<Card>): Promise<void> {
    this.dragOptions.set(null);
    const options = await this.work.transitions(event.source.data.id);
    this.dragOptions.set(options.filter((option) => option.canExecute));
  }

  /** Durante o arraste, só aceita colunas para as quais existe transição (a de origem também, para cancelar). */
  protected readonly canEnter = (drag: CdkDrag<Card>, drop: CdkDropList<BoardColumn>): boolean => {
    const options = this.dragOptions();
    const column = drop.data;
    return options === null || column.cards.some((c) => c.id === drag.data.id) || !!transitionInto(options, column);
  };

  protected isTarget(column: BoardColumn): boolean {
    const options = this.dragOptions();
    return options === null || !!transitionInto(options, column);
  }

  protected async onDrop(event: CdkDragDrop<BoardColumn, BoardColumn, Card>): Promise<void> {
    const options = this.dragOptions();
    this.dragOptions.set(null);
    if (event.previousContainer === event.container || !options) {
      return;
    }
    const option = transitionInto(options, event.container.data);
    if (option) {
      await this.move(event.item.data, option);
    }
  }

  protected async openMoveMenu(card: Card): Promise<void> {
    this.menuOptions.set(null);
    this.menuOptions.set(await this.work.transitions(card.id));
  }

  /** Executa a transição; em caso de erro o cartão fica onde estava e a tela mostra o motivo (UX/UI 6.2 e 9). */
  protected async move(card: Card, option: TransitionOption): Promise<void> {
    await this.run(async () => {
      await this.work.transition(card.id, option.id, card.version);
      await this.refresh();
      this.toast(`${card.key} movida para ${option.to.name}.`);
    });
  }

  // ---------------------------------------------------------------------------------------- apoio

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  protected initials(name: string | undefined): string {
    return (name ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join('');
  }

  private matches(...values: (string | undefined)[]): boolean {
    const term = this.filter().trim().toLocaleLowerCase('pt-BR');
    return !term || values.some((v) => v?.toLocaleLowerCase('pt-BR').includes(term));
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
    } catch (error) {
      const apiError = toApiError(error);
      this.toast(apiError.code === 'GATE_BLOCKED' ? `Bloqueado pelo gate: ${apiError.detail}` : messageOf(apiError));
      await this.refresh();
    } finally {
      this.busy.set(false);
    }
  }

  private toast(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000, horizontalPosition: 'end', verticalPosition: 'bottom' });
  }
}

/** A unidade do workspace e todas as que estão abaixo dela. */
function unitsInWorkspace(units: OrgUnit[], workspaceId: string | null): OrgUnit[] {
  if (!workspaceId) {
    return [];
  }
  const parentOf = new Map(units.map((u) => [u.id, u.parentId]));
  const inside = (id: string): boolean => {
    for (let current: string | null | undefined = id; current; current = parentOf.get(current)) {
      if (current === workspaceId) {
        return true;
      }
    }
    return false;
  };
  return units.filter((u) => inside(u.id)).sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
}
