import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, HostListener, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatMenuModule } from '@angular/material/menu';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { map } from 'rxjs';

import { ActivityService } from '../../core/activity.service';
import { ApiError, messageOf, toApiError } from '../../core/api-error';
import { LevelsService } from '../../core/levels.service';
import { WorkspaceService } from '../../core/workspace.service';
import { UnitHeader } from '../../layout/unit-header';
import { DraftMatch, DraftStore, LEAVE_MESSAGE, LeaveAware, draftBlocks, draftKey, draftMatch } from '../../knowledge/draft-store';
import { PageComments } from '../../knowledge/page-comments';
import { RichEditor } from '../../knowledge/rich-editor';
import {
  KnowledgeService,
  Page,
  PageList,
  PageNode,
  PageSection,
  PageStatus,
  PageVersion,
  STATUS_LABELS,
  pageTree,
  PageSummary,
  RankOp,
  RankRequest,
  RichDoc,
  blocksToDoc,
  dropRank,
  moveTargets,
  rankFor,
  docHasText,
  docToBlocks,
  pageLink,
} from '../../knowledge/knowledge.service';

/** Seção em edição: o documento do editor contínuo. */
interface DraftSection {
  key: string;
  title: string;
  required: boolean;
  doc: RichDoc;
}

/**
 * Wiki e Regimento da unidade ativa (Proposta 07, Fase A; UX/UI 7.12; bug #66). Cada unidade tem os seus, exclusivos:
 * o Wiki de uma unidade não mostra o de outra. No modo `wiki`, as páginas da unidade, com a página à direita; no modo
 * `regimento`, o regimento da unidade (um por unidade), com o ciclo de revisão, aprovação e publicação.
 */
@Component({
  selector: 'app-wiki',
  imports: [RichEditor, DatePipe, UnitHeader, CdkDropList, CdkDrag, MatMenuModule, MatDividerModule, PageComments],
  templateUrl: './wiki.html',
  styleUrl: './wiki.scss',
})
export class Wiki implements LeaveAware {
  private readonly knowledge = inject(KnowledgeService);
  private readonly router = inject(Router);
  private readonly activity = inject(ActivityService);
  // Comentários, "Comentar trecho" e favoritos (#85).
  private readonly comments = viewChild(PageComments);
  protected readonly favorites = signal<Set<string>>(new Set());
  protected readonly selection = signal<{ text: string; top: number; left: number } | null>(null);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly workspace = inject(WorkspaceService);
  protected readonly levels = inject(LevelsService);

  /** Vem dos dados da rota: `/wiki` ou `/regimento`. */
  readonly kind = input<'wiki' | 'regimento'>('wiki', { alias: 'mode' });
  protected readonly isRegimento = computed(() => this.kind() === 'regimento');
  /** No modo regimento: se o usuário pode criar o regimento que falta. */
  protected readonly canCreateRegimento = signal(false);
  protected readonly regimentoLoaded = signal(false);

  private readonly pageParam = toSignal(inject(ActivatedRoute).queryParamMap.pipe(map((q) => q.get('page'))), {
    initialValue: null,
  });

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly list = signal<PageList | null>(null);
  protected readonly tree = computed(() => pageTree(this.list()?.items ?? []));
  protected readonly showArchived = signal(false);
  protected readonly loadError = signal<string | null>(null);

  protected readonly page = signal<Page | null>(null);
  protected readonly mode = signal<'read' | 'edit' | 'history'>('read');
  protected readonly versions = signal<PageVersion[]>([]);
  /** Versão antiga aberta pelo histórico (só leitura). */
  protected readonly oldVersion = signal<Page | null>(null);
  protected readonly shown = computed(() => this.oldVersion() ?? this.page());

  /** Seções em edição, cada uma com o documento do editor contínuo (bug #67). */
  protected readonly draft = signal<DraftSection[]>([]);
  /** Seções da versão mostrada, já convertidas para o editor (inclui o conteúdo do editor anterior). */
  protected readonly shownDocs = computed(() =>
    (this.shown()?.sections ?? []).map((s) => {
      const doc = blocksToDoc(s.blocks);
      return { key: s.key, title: s.title, doc, empty: !docHasText(doc) };
    }),
  );
  protected readonly draftTitle = signal('');
  protected readonly draftSummary = signal('');
  // Rascunho automático neste navegador (#82).
  private readonly drafts = inject(DraftStore);
  /** Houve alteração desde que a edição começou (ou desde o rascunho recuperado). */
  protected readonly dirty = signal(false);
  /** Hora do último rascunho guardado, para o aviso na barra de salvar. */
  protected readonly draftSavedAt = signal<Date | null>(null);
  /** Rascunho encontrado ao começar a editar: recuperado ('same') ou de uma versão anterior ('older'). */
  protected readonly draftFound = signal<{ match: DraftMatch; savedAt: Date; version: number } | null>(null);
  private draftTimer: ReturnType<typeof setTimeout> | null = null;
  protected readonly conflict = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  /** Formulário de nova página: tipo e pai. */
  protected readonly creating = signal<{ type: 'PAGE' | 'REGIMENTO'; parentId: string | null } | null>(null);
  protected readonly newTitle = signal('');
  /** Ação que pede um texto: reabrir (comentário) ou arquivar (motivo). */
  protected readonly asking = signal<'reopen' | 'archive' | null>(null);
  protected readonly askText = signal('');

  constructor() {
    void this.loadFavorites();
    effect(() => {
      const unit = this.workspace.current();
      const regimento = this.isRegimento();
      if (unit) {
        untracked(() => void (regimento ? this.loadRegimento(unit.id) : this.load(unit.id)));
      }
    });
    effect(() => {
      const id = this.pageParam();
      if (this.isRegimento()) {
        return;
      }
      untracked(() => (id ? void this.open(id) : this.page.set(null)));
    });
  }

  protected async load(unitId: string): Promise<void> {
    this.loadError.set(null);
    try {
      this.list.set(await this.knowledge.pages(unitId, this.showArchived()));
    } catch (error) {
      this.list.set(null);
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async loadRegimento(unitId: string): Promise<void> {
    this.regimentoLoaded.set(false);
    this.mode.set('read');
    this.oldVersion.set(null);
    this.error.set(null);
    try {
      const result = await this.knowledge.regimento(unitId);
      this.page.set(result.regimento);
      this.canCreateRegimento.set(result.canEdit && !result.regimento);
    } catch (error) {
      this.page.set(null);
      this.loadError.set(messageOf(toApiError(error)));
    } finally {
      this.regimentoLoaded.set(true);
    }
  }

  protected toggleArchived(): void {
    this.showArchived.set(!this.showArchived());
    const unit = this.workspace.current();
    if (unit) {
      void this.load(unit.id);
    }
  }

  protected select(id: string): void {
    if (id !== this.page()?.id && !this.canLeave() && !window.confirm(LEAVE_MESSAGE)) {
      return;
    }
    if (id !== this.page()?.id) {
      this.stopEditing();
    }
    void this.router.navigate([], { queryParams: { page: id } });
  }

  /** Sair sem perguntar: fora da edição ou sem alterações (#82). */
  canLeave(): boolean {
    return this.mode() !== 'edit' || !this.dirty();
  }

  @HostListener('window:beforeunload', ['$event'])
  protected beforeUnload(event: BeforeUnloadEvent): void {
    if (!this.canLeave()) {
      this.flushDraft();
      event.preventDefault();
    }
  }

  /** Estrela da página: marca ou desmarca para a pessoa (#85). */
  protected async toggleFavorite(id: string): Promise<void> {
    const on = !this.favorites().has(id);
    try {
      await this.activity.favorite(id, on);
      const next = new Set(this.favorites());
      if (on) {
        next.add(id);
      } else {
        next.delete(id);
      }
      this.favorites.set(next);
      this.snackBar.open(on ? 'Página marcada com estrela.' : 'Estrela retirada.', undefined, { duration: 2500 });
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    }
  }

  private async loadFavorites(): Promise<void> {
    try {
      this.favorites.set(new Set((await this.activity.favorites()).map((f) => f.pageId)));
    } catch {
      // Sem a lista, a estrela começa apagada; marcar continua funcionando.
    }
  }

  /** Texto selecionado na leitura da página: mostra o botão "Comentar trecho" perto da seleção. */
  protected readSelection(): void {
    const chosen = window.getSelection();
    const text = chosen?.toString().replace(/\s+/g, ' ').trim() ?? '';
    if (!chosen || chosen.rangeCount === 0 || text.length < 2) {
      this.selection.set(null);
      return;
    }
    const rect = chosen.getRangeAt(0).getBoundingClientRect();
    this.selection.set({ text, top: Math.max(8, rect.top - 40), left: Math.max(8, rect.left) });
  }

  protected commentSelection(): void {
    const chosen = this.selection();
    if (chosen) {
      this.comments()?.quoteText(chosen.text);
      window.getSelection()?.removeAllRanges();
      this.selection.set(null);
    }
  }

  private async open(id: string): Promise<void> {
    this.selection.set(null);
    this.mode.set('read');
    this.oldVersion.set(null);
    this.error.set(null);
    this.conflict.set(false);
    try {
      this.page.set(await this.knowledge.page(id));
      this.activity.visit('PAGE', id);
    } catch (error) {
      this.page.set(null);
      this.error.set(messageOf(toApiError(error)));
    }
  }

  // ------------------------------------------------------------------------------------------ criação

  protected startCreate(type: 'PAGE' | 'REGIMENTO', parentId: string | null): void {
    this.creating.set({ type, parentId });
    this.newTitle.set(type === 'REGIMENTO' ? `Regimento — ${this.workspace.current()?.name ?? ''}` : '');
    this.error.set(null);
  }

  protected async create(event: Event): Promise<void> {
    event.preventDefault();
    const form = this.creating();
    const unit = this.workspace.current();
    if (!form || !unit) {
      return;
    }
    await this.run(async () => {
      if (form.type === 'REGIMENTO') {
        await this.knowledge.createRegimento(unit.id, this.newTitle().trim());
        this.creating.set(null);
        await this.loadRegimento(unit.id);
        return;
      }
      const page = await this.knowledge.create(unit.id, this.newTitle().trim(), form.parentId);
      this.creating.set(null);
      await this.load(unit.id);
      this.select(page.id);
    });
  }

  // ------------------------------------------------------------------------------------------ edição

  protected startEdit(): void {
    const page = this.page();
    if (!page) {
      return;
    }
    this.draft.set(
      page.sections.map((s) => ({ key: s.key, title: s.title, required: s.required, doc: blocksToDoc(s.blocks) })),
    );
    this.draftTitle.set(page.title);
    this.draftSummary.set('');
    this.conflict.set(false);
    this.error.set(null);
    this.dirty.set(false);
    this.draftSavedAt.set(null);
    this.draftFound.set(null);
    const stored = this.drafts.read(draftKey('page', page.id));
    const match = draftMatch(stored, page.currentVersion);
    if (stored && match) {
      this.draftFound.set({ match, savedAt: new Date(stored.savedAt), version: stored.baseVersion });
      if (match === 'same') {
        this.applyStoredDraft();
      }
    }
    this.mode.set('edit');
  }

  /** Repõe no editor o rascunho guardado; o de versão anterior só quando a pessoa pede. */
  protected applyStoredDraft(): void {
    const page = this.page();
    const stored = page ? this.drafts.read(draftKey('page', page.id)) : null;
    if (!page || !stored) {
      return;
    }
    this.draft.set(
      this.draft().map((s) => {
        const blocks = draftBlocks(stored, s.key);
        return blocks ? { ...s, doc: blocksToDoc(blocks) } : s;
      }),
    );
    if (stored.title) {
      this.draftTitle.set(stored.title);
    }
    const found = this.draftFound();
    this.draftFound.set(found ? { ...found, match: 'same' } : null);
    this.dirty.set(true);
    this.draftSavedAt.set(new Date(stored.savedAt));
  }

  /** Apaga o rascunho guardado e volta ao texto da versão atual. */
  protected discardStoredDraft(): void {
    const page = this.page();
    if (page) {
      this.drafts.clear(draftKey('page', page.id));
    }
    this.startEdit();
  }

  protected setDoc(index: number, doc: RichDoc): void {
    this.draft.set(this.draft().map((s, i) => (i === index ? { ...s, doc } : s)));
    this.touch();
  }

  protected setDraftTitle(title: string): void {
    this.draftTitle.set(title);
    this.touch();
  }

  /** Cancelar: com alterações, pergunta e apaga o rascunho. */
  protected cancelEdit(): void {
    if (this.dirty() && !window.confirm('Descartar as alterações? O rascunho guardado neste navegador também será apagado.')) {
      return;
    }
    const page = this.page();
    if (page) {
      this.drafts.clear(draftKey('page', page.id));
    }
    this.stopEditing();
  }

  private stopEditing(): void {
    if (this.draftTimer) {
      clearTimeout(this.draftTimer);
      this.draftTimer = null;
    }
    this.dirty.set(false);
    this.draftFound.set(null);
    this.draftSavedAt.set(null);
    if (this.mode() === 'edit') {
      this.mode.set('read');
    }
  }

  /** Marca a alteração e guarda o rascunho pouco depois da última tecla. */
  private touch(): void {
    this.dirty.set(true);
    if (this.draftTimer) {
      clearTimeout(this.draftTimer);
    }
    this.draftTimer = setTimeout(() => this.flushDraft(), 800);
  }

  private flushDraft(): void {
    this.draftTimer = null;
    const page = this.page();
    if (!page || this.mode() !== 'edit' || !this.dirty()) {
      return;
    }
    const savedAt = new Date();
    const ok = this.drafts.write(draftKey('page', page.id), {
      baseVersion: page.currentVersion,
      title: this.draftTitle(),
      sections: this.draft().map((s) => ({ key: s.key, blocks: docToBlocks(s.doc) })),
      savedAt: savedAt.toISOString(),
    });
    if (ok) {
      this.draftSavedAt.set(savedAt);
    }
  }

  protected async save(): Promise<void> {
    const page = this.page();
    if (!page) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const sections: PageSection[] = this.draft().map((s) => ({
        key: s.key,
        title: s.title,
        required: s.required,
        blocks: docToBlocks(s.doc),
      }));
      let saved = await this.knowledge.save(page.id, page.currentVersion, sections, this.draftSummary().trim());
      const title = this.draftTitle().trim();
      if (title && title !== page.title) {
        saved = await this.knowledge.update(page.id, { title });
      }
      this.drafts.clear(draftKey('page', page.id));
      this.stopEditing();
      this.page.set(saved);
      await this.reloadList();
    } catch (error) {
      const api = toApiError(error);
      // Conflito de edição (UX/UI 6.8): o rascunho fica na tela para a pessoa copiar o que precisar.
      if (api.code === 'VERSION_CONFLICT') {
        this.conflict.set(true);
      } else {
        this.error.set(messageOf(api));
      }
    } finally {
      this.busy.set(false);
    }
  }

  protected async reloadAfterConflict(): Promise<void> {
    const page = this.page();
    if (page) {
      await this.open(page.id);
    }
  }

  // ------------------------------------------------------------------------------------------ histórico

  protected async showHistory(): Promise<void> {
    const page = this.page();
    if (!page) {
      return;
    }
    await this.run(async () => {
      this.versions.set(await this.knowledge.versions(page.id));
      this.mode.set('history');
    });
  }

  protected async openVersion(number: number): Promise<void> {
    const page = this.page();
    if (!page) {
      return;
    }
    await this.run(async () => {
      this.oldVersion.set(number === page.currentVersion ? null : await this.knowledge.version(page.id, number));
      this.mode.set('read');
    });
  }

  // ------------------------------------------------------------------------------------------ ciclo e arquivo

  protected async changeStatus(status: PageStatus, comment?: string): Promise<void> {
    const page = this.page();
    if (!page) {
      return;
    }
    await this.run(async () => {
      this.page.set(await this.knowledge.changeStatus(page.id, status, comment));
      this.asking.set(null);
      await this.reloadList();
    });
  }

  protected ask(action: 'reopen' | 'archive'): void {
    this.asking.set(action);
    this.askText.set('');
  }

  protected async confirmAsk(event: Event): Promise<void> {
    event.preventDefault();
    const page = this.page();
    if (!page) {
      return;
    }
    if (this.asking() === 'reopen') {
      await this.changeStatus('DRAFT', this.askText().trim());
      return;
    }
    await this.run(async () => {
      this.page.set(await this.knowledge.archive(page.id, this.askText().trim()));
      this.asking.set(null);
      await this.reloadList();
    });
  }

  protected async restore(): Promise<void> {
    const page = this.page();
    if (!page) {
      return;
    }
    await this.run(async () => {
      this.page.set(await this.knowledge.restore(page.id));
      await this.reloadList();
    });
  }

  // ------------------------------------------------------------------------------------------ organizar (PROP-002)

  /** Mover para…: a página escolhida no menu ⋯ e o destino ('' = raiz). */
  protected readonly moving = signal<PageNode | null>(null);
  protected readonly moveTarget = signal('');
  protected readonly targets = computed(() => {
    const node = this.moving();
    return node ? moveTargets(this.list()?.items ?? [], node.id) : [];
  });

  protected canOrganize(id: string, op: RankOp): boolean {
    return rankFor(this.list()?.items ?? [], id, op) !== null;
  }

  protected async organize(id: string, op: RankOp): Promise<void> {
    const request = rankFor(this.list()?.items ?? [], id, op);
    if (request) {
      await this.applyRank(id, request);
    }
  }

  protected openMove(node: PageNode): void {
    this.renaming.set(null);
    this.archiving.set(null);
    this.moveTarget.set(node.parentId ?? '');
    this.moving.set(node);
  }

  protected async confirmMove(event: Event): Promise<void> {
    event.preventDefault();
    const node = this.moving();
    if (node) {
      await this.applyRank(node.id, { parentId: this.moveTarget() || null });
      this.moving.set(null);
    }
  }

  /** Arrastar e soltar: a página vai para onde foi solta, no nível da linha vizinha (PROP-002). */
  protected async dropped(event: CdkDragDrop<PageSummary[]>): Promise<void> {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const move = dropRank(this.tree(), event.previousIndex, event.currentIndex);
    if (move) {
      await this.applyRank(move.id, move.request);
    } else {
      this.error.set('Uma página não pode ir para dentro dela mesma.');
    }
  }

  private async applyRank(id: string, request: RankRequest): Promise<void> {
    await this.run(async () => {
      const moved = await this.knowledge.rank(id, request);
      await this.reloadList();
      if (this.page()?.id === moved.id) {
        this.page.set(moved);
      }
    });
  }

  // ------------------------------------------------------------------------------------------ menu ⋯ da página

  /** Renomear e Arquivar pelo menu ⋯: a página escolhida e o texto do formulário no topo do conteúdo. */
  protected readonly renaming = signal<PageNode | null>(null);
  protected readonly archiving = signal<PageNode | null>(null);
  protected readonly menuText = signal('');

  protected openRename(node: PageNode): void {
    this.archiving.set(null);
    this.moving.set(null);
    this.menuText.set(node.title);
    this.renaming.set(node);
  }

  protected async confirmRename(event: Event): Promise<void> {
    event.preventDefault();
    const node = this.renaming();
    const title = this.menuText().trim();
    if (!node || !title) {
      return;
    }
    await this.run(async () => {
      const renamed = await this.knowledge.update(node.id, { title });
      this.renaming.set(null);
      await this.reloadList();
      if (this.page()?.id === renamed.id) {
        this.page.set(renamed);
      }
    });
  }

  protected async copyLink(node: PageNode): Promise<void> {
    const link = pageLink(window.location.href, node.id);
    try {
      await navigator.clipboard.writeText(link);
      this.snackBar.open('Link copiado.', 'Fechar', { duration: 3000 });
    } catch {
      this.snackBar.open(`Copie o link: ${link}`, 'Fechar');
    }
  }

  protected async copyPage(node: PageNode): Promise<void> {
    await this.run(async () => {
      const copy = await this.knowledge.copy(node.id);
      await this.reloadList();
      this.select(copy.id);
      this.snackBar.open('Cópia criada.', 'Fechar', { duration: 3000 });
    });
  }

  protected openArchive(node: PageNode): void {
    this.renaming.set(null);
    this.moving.set(null);
    this.menuText.set('');
    this.archiving.set(node);
  }

  protected async confirmArchive(event: Event): Promise<void> {
    event.preventDefault();
    const node = this.archiving();
    if (!node) {
      return;
    }
    await this.run(async () => {
      const archived = await this.knowledge.archive(node.id, this.menuText().trim());
      this.archiving.set(null);
      await this.reloadList();
      if (this.page()?.id === archived.id) {
        this.page.set(archived);
      }
    });
  }

  protected async restoreNode(node: PageNode): Promise<void> {
    await this.run(async () => {
      const restored = await this.knowledge.restore(node.id);
      await this.reloadList();
      if (this.page()?.id === restored.id) {
        this.page.set(restored);
      }
    });
  }

  // ------------------------------------------------------------------------------------------ apoio

  /** Títulos das seções obrigatórias ainda vazias, para o aviso antes da aprovação. */
  protected readonly missingTitles = computed(() => {
    const page = this.page();
    return (page?.missingSections ?? [])
      .map((key) => page?.sections.find((s) => s.key === key)?.title ?? key)
      .join(', ');
  });

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  private async reloadList(): Promise<void> {
    const unit = this.workspace.current();
    if (unit && !this.isRegimento()) {
      this.list.set(await this.knowledge.pages(unit.id, this.showArchived()));
    }
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(messageOf(toApiError(error) as ApiError));
    } finally {
      this.busy.set(false);
    }
  }
}
