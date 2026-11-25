import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatMenuModule } from '@angular/material/menu';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

import { ApiError, messageOf, toApiError } from '../../core/api-error';
import { LevelsService } from '../../core/levels.service';
import { WorkspaceService } from '../../core/workspace.service';
import { UnitHeader } from '../../layout/unit-header';
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
  imports: [RichEditor, DatePipe, UnitHeader, CdkDropList, CdkDrag, MatMenuModule],
  templateUrl: './wiki.html',
  styleUrl: './wiki.scss',
})
export class Wiki {
  private readonly knowledge = inject(KnowledgeService);
  private readonly router = inject(Router);
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
    void this.router.navigate([], { queryParams: { page: id } });
  }

  private async open(id: string): Promise<void> {
    this.mode.set('read');
    this.oldVersion.set(null);
    this.error.set(null);
    this.conflict.set(false);
    try {
      this.page.set(await this.knowledge.page(id));
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
    this.mode.set('edit');
  }

  protected setDoc(index: number, doc: RichDoc): void {
    this.draft.set(this.draft().map((s, i) => (i === index ? { ...s, doc } : s)));
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
      this.page.set(saved);
      this.mode.set('read');
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
