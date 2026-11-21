import { CdkDrag, CdkDragDrop, CdkDragStart, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

import { OrgUnit, OrgUnitDetail, OrganizationService } from '../../admin/organization.service';
import { LevelsService } from '../../core/levels.service';
import { ApiError, fieldError, messageOf, toApiError } from '../../core/api-error';
import { WorkspaceService } from '../../core/workspace.service';
import { BoardConfig } from '../../work/board-config';
import { SquadBacklog } from '../../work/squad-backlog';
import { SquadSprints } from '../../work/squad-sprints';
import { LEVEL_SETUPS, LevelSetup, MANAGE_ROLES_NOTE } from './level-setup';
import {
  Artifact,
  ArtifactType,
  Board,
  BoardColumn,
  BoardView,
  Card,
  Sprint,
  TransitionOption,
  WorkService,
  executable,
  transitionInto,
  unitsOfLevelIn,
} from '../../work/work.service';

/**
 * Quadro de um Flight Level: Portfolio (FL3, UX/UI 7.3), Tactical (FL2, 7.5) e Squad (FL1, 7.7), em quadro ou lista. A
 * rota informa o nível ({@link LEVEL_SETUPS}). Mover um cartão é executar a transição (UX/UI 6.2): o cartão só muda de
 * coluna depois da resposta de sucesso.
 */
@Component({
  selector: 'app-level-board',
  imports: [CdkDropListGroup, CdkDropList, CdkDrag, MatMenuModule, MatSnackBarModule, BoardConfig, SquadBacklog, SquadSprints],
  templateUrl: './level-board.html',
  styleUrl: './level-board.scss',
})
export class LevelBoard {
  private readonly work = inject(WorkService);
  private readonly organization = inject(OrganizationService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly workspace = inject(WorkspaceService);
  protected readonly levels = inject(LevelsService);
  private readonly router = inject(Router);
  /** Aba pedida no endereço (?tab=backlog|sprints): o menu da Squad leva direto a ela (bug #66). */
  private readonly tabParam = toSignal(inject(ActivatedRoute).queryParamMap.pipe(map((q) => q.get('tab'))), {
    initialValue: null,
  });

  /** Chave do nível, vinda dos dados da rota. */
  readonly screen = input<keyof typeof LEVEL_SETUPS>('portfolio');
  protected readonly setup = computed<LevelSetup>(() => LEVEL_SETUPS[this.screen()]);
  /** Título da tela e rótulo da unidade: o nome do nível ligado ao Flight Level, vindo da configuração. */
  protected readonly levelName = computed(() => this.levels.nameOf(this.setup().level));
  /** Unidade ativa com o caminho até a raiz e os vínculos, para a trilha (Proposta 06, NAV-008 e NAV-010). */
  protected readonly context = signal<OrgUnitDetail | null>(null);
  protected readonly newLabel = computed(() => {
    const types = this.creatableTypes();
    return types.length === 1 ? `Criar ${types[0]!.name}` : 'Novo item';
  });
  protected readonly manageNote = MANAGE_ROLES_NOTE;
  /** Tipos de artefato da instalação (nome, pais permitidos), para os cartões e o formulário. */
  protected readonly types = signal<ArtifactType[]>([]);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly board = signal<Board | null>(null);
  protected readonly view = signal<BoardView | null>(null);
  protected readonly mode = signal<'quadro' | 'lista'>('quadro');
  /** Abas da tela de FL1 (UX/UI 7.7): Quadro, Backlog e Sprints. */
  protected readonly tab = signal<'quadro' | 'backlog' | 'sprints'>('quadro');
  /** Sprints planejadas e ativas das squads do workspace, para o seletor do quadro (vazio = sem seletor). */
  protected readonly sprintOptions = signal<Sprint[]>([]);
  protected readonly sprintFilter = signal('');
  protected readonly filter = signal('');
  protected readonly list = signal<Artifact[]>([]);
  protected readonly busy = signal(false);
  protected readonly configuring = signal(false);
  protected readonly onlyBlocked = signal(false);
  /** Cartão sendo marcado como bloqueado, com o motivo digitado. */
  protected readonly blocking = signal<Card | null>(null);
  protected readonly blockReason = signal('');

  /** Colunas para onde o cartão arrastado pode ir; as demais ficam esmaecidas (UX/UI 6.2). */
  protected readonly dragOptions = signal<TransitionOption[] | null>(null);
  protected readonly menuOptions = signal<TransitionOption[] | null>(null);

  // Novo item
  protected readonly creating = signal(false);
  protected readonly newType = signal('');
  protected readonly newTitle = signal('');
  protected readonly newDescription = signal('');
  protected readonly newUnit = signal('');
  protected readonly newParent = signal('');
  protected readonly createError = signal<ApiError | null>(null);
  protected readonly levelUnits = signal<OrgUnit[]>([]);
  /** Itens que podem ser pai do tipo escolhido e que o usuário lê. */
  protected readonly parentCandidates = signal<Artifact[]>([]);
  /** Na ordem definida para a tela (História primeiro no Squad, Feature primeiro no Tactical). */
  protected readonly creatableTypes = computed(() =>
    this.setup()
      .creatableTypes.map((key) => this.types().find((t) => t.key === key))
      .filter((t): t is ArtifactType => !!t),
  );
  protected readonly selectedType = computed(() => this.types().find((t) => t.key === this.newType()) ?? null);

  /** Só decide o que mostrar; quem decide o acesso é a API (SSD 2). */
  protected readonly canCreate = computed(() => this.workspace.hasRole(...this.setup().createRoles));
  protected readonly canManageBoards = computed(() =>
    this.workspace.hasRole('PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD'),
  );

  protected readonly upstream = computed(() => this.filtered().filter((c) => c.phase === 'UPSTREAM'));
  protected readonly downstream = computed(() => this.filtered().filter((c) => c.phase === 'DOWNSTREAM'));
  protected readonly filteredList = computed(() => this.list().filter((a) => this.matches(a.key, a.title, a.owner?.name)));

  private readonly filtered = computed<BoardColumn[]>(() =>
    (this.view()?.columns ?? []).map((column) => ({
      ...column,
      cards: column.cards.filter(
        (card) => (!this.onlyBlocked() || card.blocked) && this.matches(card.key, card.title, card.owner?.name),
      ),
    })),
  );

  protected readonly fieldError = fieldError;
  protected readonly messageOf = messageOf;

  constructor() {
    // Recarrega quando a rota troca de nível (Portfolio, Tactical, Squad reutilizam o componente).
    effect(() => {
      this.screen();
      untracked(() => {
        this.creating.set(false);
        this.configuring.set(false);
        this.board.set(null);
        this.tab.set('quadro');
        this.sprintFilter.set('');
        void this.load();
      });
    });
    effect(() => {
      const param = this.tabParam();
      const fl1 = this.setup().level === 'FL1';
      const tab = fl1 && (param === 'backlog' || param === 'sprints') ? param : 'quadro';
      untracked(() => {
        if (tab !== this.tab()) {
          void this.openTab(tab);
        }
      });
    });
  }

  protected typeName(key: string): string {
    return this.types().find((t) => t.key === key)?.name ?? key;
  }

  protected async load(): Promise<void> {
    const workspaceId = this.workspace.currentWorkspaceId();
    if (!workspaceId) {
      return;
    }
    this.loading.set(true);
    this.loadError.set(null);
    try {
      if (this.types().length === 0) {
        this.types.set(await this.work.artifactTypes());
      }
      await this.levels.load();
      this.context.set(await this.organization.unit(workspaceId));
      await this.loadSprintOptions(workspaceId);
      const boards = await this.work.boards(workspaceId, this.setup().level);
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
      board ? this.work.boardView(board.id, this.sprintFilter() || null) : Promise.resolve(null),
      this.work.artifacts(this.setup().level),
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
      this.board.set(await this.work.createBoard(workspaceId, this.setup().level));
      await this.refresh();
      this.toast('Quadro criado.');
    });
  }

  // ---------------------------------------------------------------------------------------- novo item

  protected async openCreate(): Promise<void> {
    this.creating.set(true);
    this.createError.set(null);
    // Define o tipo padrão já, antes de esperar a API: assim a escolha do usuário feita nesse meio-tempo não é desfeita.
    const initial = this.creatableTypes()[0]?.key ?? '';
    this.newType.set(initial);
    const units = await this.organization.allUnits();
    const options = unitsInWorkspace(units, this.workspace.currentWorkspaceId()).filter(
      (unit) => unit.active && unit.level.flightLevel === this.setup().level,
    );
    this.levelUnits.set(options);
    this.newUnit.set(options[0]?.id ?? '');
    if (this.newType() === initial) {
      await this.chooseType(initial);
    }
  }

  /** Troca o tipo e carrega os possíveis pais: itens que o usuário lê, dos tipos permitidos (SSD 8.1). */
  protected async chooseType(key: string): Promise<void> {
    this.newType.set(key);
    this.newParent.set('');
    const type = this.selectedType();
    if (!type || type.parentTypes.length === 0) {
      this.parentCandidates.set([]);
      return;
    }
    const levels = [...new Set(this.types().filter((t) => type.parentTypes.includes(t.key)).map((t) => t.level))];
    const lists = await Promise.all(levels.map((level) => this.work.artifacts(level)));
    if (this.newType() !== key) {
      return; // o usuário já trocou de tipo enquanto os pais carregavam
    }
    const candidates = lists.flat().filter((a) => type.parentTypes.includes(a.type));
    this.parentCandidates.set(candidates);
    if (type.parentRequired) {
      this.newParent.set(candidates[0]?.id ?? '');
    }
  }

  protected async createItem(): Promise<void> {
    this.createError.set(null);
    this.busy.set(true);
    try {
      const created = await this.work.createArtifact({
        type: this.newType(),
        title: this.newTitle(),
        description: this.newDescription() || undefined,
        orgUnitId: this.newUnit(),
        parentId: this.newParent() || undefined,
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
    // Só colunas com transição executável agora: permissão e sem regra pendente (gate ou limite de WIP).
    this.dragOptions.set(executable(options));
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

  // ---------------------------------------------------------------------------------------- sprints (FL1)

  /** Só a tela de FL1 tem sprints; o seletor aparece quando alguma squad do workspace usa sprint (UX/UI 7.7). */
  private async loadSprintOptions(workspaceId: string): Promise<void> {
    if (this.setup().level !== 'FL1') {
      this.sprintOptions.set([]);
      return;
    }
    const squads = unitsOfLevelIn(await this.organization.allUnits(), workspaceId, 'FL1');
    const lists = await Promise.all(squads.map((s) => this.work.sprints(s.id)));
    this.sprintOptions.set(lists.flat().filter((s) => s.state !== 'CLOSED'));
  }

  protected async filterBySprint(id: string): Promise<void> {
    this.sprintFilter.set(id);
    await this.refresh();
  }

  /** As abas mudam o endereço, para o menu e o botão voltar acompanharem. */
  protected showTab(tab: 'quadro' | 'backlog' | 'sprints'): void {
    void this.router.navigate([], { queryParams: { tab: tab === 'quadro' ? null : tab }, queryParamsHandling: 'merge' });
  }

  private async openTab(tab: 'quadro' | 'backlog' | 'sprints'): Promise<void> {
    this.tab.set(tab);
    if (tab === 'quadro') {
      const workspaceId = this.workspace.currentWorkspaceId();
      if (workspaceId) {
        await this.loadSprintOptions(workspaceId);
      }
      await this.refresh();
    }
  }

  // ---------------------------------------------------------------------------------------- configurar e bloquear

  protected async onConfigured(board: Board): Promise<void> {
    this.board.set(board);
    // Recarrega antes de fechar o painel: o quadro só aparece de novo já com a configuração nova.
    await this.refresh();
    this.configuring.set(false);
    this.toast('Quadro configurado.');
  }

  protected startBlock(card: Card): void {
    this.blocking.set(card);
    this.blockReason.set(card.blockedReason ?? '');
  }

  protected async confirmBlock(): Promise<void> {
    const card = this.blocking();
    if (!card) {
      return;
    }
    await this.run(async () => {
      await this.work.setBlocked(card.id, true, this.blockReason().trim() || null, card.version);
      this.blocking.set(null);
      await this.refresh();
      this.toast(`${card.key} marcada como bloqueada.`);
    });
  }

  protected async unblock(card: Card): Promise<void> {
    await this.run(async () => {
      await this.work.setBlocked(card.id, false, null, card.version);
      await this.refresh();
      this.toast(`${card.key} desbloqueada.`);
    });
  }

  /** Texto do contador da coluna: "n / limite" quando há limite de WIP (Proposta 04, 7.1.2). */
  protected counter(column: BoardColumn): string {
    return column.wipLimit === null ? `${column.cards.length}` : `${column.wipCount ?? 0} / ${column.wipLimit}`;
  }

  protected wipTitle(column: BoardColumn): string {
    if (column.wipLimit === null) {
      return 'Sem limite de WIP';
    }
    const policy = column.wipPolicy === 'BLOCK' ? 'impede a entrada quando cheia' : 'só avisa';
    const states = { NONE: '', UNDER: 'abaixo do limite', AT: 'limite atingido', OVER: 'limite ultrapassado' };
    return `Limite de WIP ${column.wipLimit} (${policy}); ${states[column.wipState]}.`;
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
