import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

import { ApiError, messageOf, toApiError } from '../../core/api-error';
import { LevelsService } from '../../core/levels.service';
import { WorkspaceService } from '../../core/workspace.service';
import { UnitHeader } from '../../layout/unit-header';
import { BlockEditor } from '../../knowledge/block-editor';
import { BlockView } from '../../knowledge/block-view';
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
} from '../../knowledge/knowledge.service';

/** Conhecimento publicado de uma unidade acima da ativa (Proposta 07, KNOW-030; SSD 2.1, regra 9). */
interface AncestorSpace {
  id: string;
  name: string;
  pages: PageNode[];
}

/**
 * Wiki e Regimento da unidade ativa (Proposta 07, Fase A; UX/UI 7.12; bug #66). São independentes: no modo `wiki`, as
 * páginas da unidade e o conhecimento publicado das unidades acima, com a página à direita; no modo `regimento`, o
 * regimento da unidade (um por unidade), com o ciclo de revisão, aprovação e publicação.
 */
@Component({
  selector: 'app-wiki',
  imports: [BlockView, BlockEditor, DatePipe, UnitHeader],
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
  protected readonly ancestors = signal<AncestorSpace[]>([]);
  protected readonly loadError = signal<string | null>(null);

  protected readonly page = signal<Page | null>(null);
  protected readonly mode = signal<'read' | 'edit' | 'history'>('read');
  protected readonly versions = signal<PageVersion[]>([]);
  /** Versão antiga aberta pelo histórico (só leitura). */
  protected readonly oldVersion = signal<Page | null>(null);
  protected readonly shown = computed(() => this.oldVersion() ?? this.page());

  protected readonly draft = signal<PageSection[]>([]);
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
    await this.loadAncestors(unitId);
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

  private async loadAncestors(unitId: string): Promise<void> {
    const nodes = this.workspace.navigation();
    const byId = new Map(nodes.map((n) => [n.id, n]));
    const chain: { id: string; name: string }[] = [];
    for (let id = byId.get(unitId)?.parentId; id; id = byId.get(id)?.parentId) {
      chain.unshift({ id, name: byId.get(id)?.name ?? '' });
    }
    const spaces: AncestorSpace[] = [];
    for (const unit of chain) {
      try {
        const list = await this.knowledge.pages(unit.id);
        if (list.items.length > 0) {
          spaces.push({ ...unit, pages: pageTree(list.items) });
        }
      } catch {
        // Sem acesso ao conhecimento da unidade: ela não aparece.
      }
    }
    this.ancestors.set(spaces);
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
    this.draft.set(structuredClone(page.sections));
    this.draftTitle.set(page.title);
    this.draftSummary.set('');
    this.conflict.set(false);
    this.error.set(null);
    this.mode.set('edit');
  }

  protected setBlocks(index: number, blocks: PageSection['blocks']): void {
    this.draft.set(this.draft().map((s, i) => (i === index ? { ...s, blocks } : s)));
  }

  protected async save(): Promise<void> {
    const page = this.page();
    if (!page) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      let saved = await this.knowledge.save(page.id, page.currentVersion, this.draft(), this.draftSummary().trim());
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
