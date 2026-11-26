import { DatePipe, Location } from '@angular/common';
import { Component, HostListener, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { messageOf, toApiError } from '../../core/api-error';
import {
  ArtifactDocSummary,
  ArtifactDocVersion,
  ArtifactDocument,
  ArtifactDocumentsService,
  DOC_STATUS_LABELS,
  DOC_TYPES,
  DOC_TYPE_LABELS,
  DocType,
  TYPES_WITH_DOCUMENTS,
  sectionIndex,
} from '../../knowledge/artifact-documents.service';
import { ActivityService } from '../../core/activity.service';
import { DocOutline } from '../../knowledge/doc-outline';
import { ArtifactGates } from './artifact-gates';
import { DraftMatch, DraftStore, LEAVE_MESSAGE, LeaveAware, draftBlocks, draftKey, draftMatch } from '../../knowledge/draft-store';
import { RichDoc, blocksToDoc, docToBlocks } from '../../knowledge/knowledge.service';
import { RichEditor } from '../../knowledge/rich-editor';
import { Artifact, ArtifactPatch, PersonRef, SIZES, WorkService } from '../../work/work.service';

/** Campo da Visão geral aberto para edição. */
export type EditableField = 'title' | 'description' | 'size' | PersonField;
export type PersonField = 'ownerId' | 'technicalLeadId' | 'qaOwnerId';

/** Rótulo e valor atual de cada campo de pessoa. */
export const PERSON_FIELDS: { field: PersonField; label: string; of: (a: Artifact) => PersonRef | null | undefined }[] = [
  { field: 'ownerId', label: 'Responsável', of: (a) => a.owner },
  { field: 'technicalLeadId', label: 'Tech Lead', of: (a) => a.technicalLead },
  { field: 'qaOwnerId', label: 'QA', of: (a) => a.qaOwner },
];

interface DraftSection {
  key: string;
  title: string;
  required: boolean;
  doc: RichDoc;
}

/**
 * Página do artefato (UX/UI 7.4 e 7.6; #63): Visão geral e, na Iniciativa e na Feature, uma aba por documento (PRD,
 * SAD, SSD, UX/UI e QA), com o editor contínuo, versões, aprovação e reabertura (UX/UI 6.6 e 6.7). A aba fica em
 * `?tab=`. Os gates por documento vêm na entrega dos gates (SSD 5).
 */
@Component({
  selector: 'app-artifact-page',
  imports: [RouterLink, DatePipe, RichEditor, DocOutline, ArtifactGates],
  templateUrl: './artifact-page.html',
  styleUrls: ['../wiki/wiki.scss', './artifact-page.scss'],
})
export class ArtifactPage implements LeaveAware {
  private readonly route = inject(ActivatedRoute);
  private readonly work = inject(WorkService);
  private readonly documents = inject(ArtifactDocumentsService);
  private readonly location = inject(Location);
  private readonly router = inject(Router);
  private readonly activity = inject(ActivityService);
  // Rascunho automático neste navegador (#82).
  private readonly drafts = inject(DraftStore);
  protected readonly dirty = signal(false);
  protected readonly draftSavedAt = signal<Date | null>(null);
  protected readonly draftFound = signal<{ match: DraftMatch; savedAt: Date; version: number } | null>(null);
  private draftTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly statusLabels = DOC_STATUS_LABELS;
  protected readonly typeLabels = DOC_TYPE_LABELS;

  private readonly key = toSignal(this.route.paramMap.pipe(map((p) => p.get('key') ?? '')), { initialValue: '' });
  private readonly tabParam = toSignal(this.route.queryParamMap.pipe(map((q) => q.get('tab'))), { initialValue: null });

  protected readonly artifact = signal<Artifact | null>(null);
  protected readonly summaries = signal<ArtifactDocSummary[]>([]);
  protected readonly loadError = signal<string | null>(null);

  protected readonly hasDocuments = computed(() => TYPES_WITH_DOCUMENTS.includes(this.artifact()?.type ?? ''));
  protected readonly tabs = computed(() => (this.hasDocuments() ? DOC_TYPES : []));
  /** Aba atual: um tipo de documento, ou nulo para a Visão geral. */
  protected readonly docType = computed<DocType | null>(() => {
    const tab = this.tabParam();
    return this.hasDocuments() && DOC_TYPES.includes(tab as DocType) ? (tab as DocType) : null;
  });

  protected readonly document = signal<ArtifactDocument | null>(null);
  protected readonly oldVersion = signal<ArtifactDocument | null>(null);
  protected readonly shown = computed(() => this.oldVersion() ?? this.document());
  protected readonly index = computed(() => sectionIndex(this.shown()?.sections ?? []));
  protected readonly shownDocs = computed(() =>
    (this.shown()?.sections ?? []).map((s) => ({ key: s.key, title: s.title, required: s.required, doc: blocksToDoc(s.blocks) })),
  );
  /** Documentos da leitura, para o sumário automático (#86). */
  protected readonly readingDocs = computed(() => this.shownDocs().map((s) => s.doc));
  protected readonly missingTitles = computed(() => {
    const doc = this.document();
    return (doc?.missingSections ?? []).map((k) => doc?.sections.find((s) => s.key === k)?.title ?? k).join(', ');
  });

  protected readonly mode = signal<'read' | 'edit' | 'history'>('read');
  protected readonly draft = signal<DraftSection[]>([]);
  protected readonly draftSummary = signal('');
  protected readonly versions = signal<ArtifactDocVersion[]>([]);
  protected readonly conflict = signal(false);
  protected readonly reopening = signal(false);
  protected readonly reopenComment = signal('');
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  // Edição dos dados do artefato, um campo por vez, clicando nele.
  protected readonly sizes = SIZES;
  protected readonly personFields = PERSON_FIELDS;
  protected readonly editing = signal<EditableField | null>(null);
  protected readonly textDraft = signal('');
  protected readonly personQuery = signal('');
  protected readonly personResults = signal<PersonRef[]>([]);
  protected readonly fieldNotice = signal<string | null>(null);
  private searchSeq = 0;

  constructor() {
    effect(() => {
      const key = this.key();
      if (key) {
        untracked(() => void this.load(key));
      }
    });
    effect(() => {
      const type = this.docType();
      const artifact = this.artifact();
      if (artifact) {
        untracked(() => void (type ? this.openDocument(artifact.key, type) : this.document.set(null)));
      }
    });
  }

  /** Recarrega o artefato depois de mudar de status pelos Próximos passos (#78). */
  protected reload(): void {
    const a = this.artifact();
    if (a) {
      void this.load(a.key);
    }
  }

  private async load(key: string): Promise<void> {
    this.loadError.set(null);
    try {
      const artifact = await this.work.artifact(key);
      this.artifact.set(artifact);
      this.activity.visit('ARTIFACT', artifact.key);
      this.summaries.set(TYPES_WITH_DOCUMENTS.includes(artifact.type) ? (await this.documents.list(artifact.key)).items : []);
    } catch (error) {
      this.artifact.set(null);
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async openDocument(key: string, type: DocType): Promise<void> {
    this.stopEditing();
    this.mode.set('read');
    this.oldVersion.set(null);
    this.reopening.set(false);
    this.conflict.set(false);
    this.error.set(null);
    try {
      this.document.set(await this.documents.get(key, type));
      this.activity.visit('DOCUMENT', `${key}/${type}`);
    } catch (error) {
      this.document.set(null);
      this.error.set(messageOf(toApiError(error)));
    }
  }

  protected startField(field: EditableField): void {
    const a = this.artifact();
    if (!a?.canEdit || this.busy()) {
      return;
    }
    this.fieldNotice.set(null);
    this.error.set(null);
    this.textDraft.set(field === 'title' ? a.title : field === 'description' ? (a.description ?? '') : '');
    this.personQuery.set('');
    this.personResults.set([]);
    this.editing.set(field);
    if (field === 'ownerId' || field === 'technicalLeadId' || field === 'qaOwnerId') {
      void this.searchPeople('');
    }
  }

  protected cancelField(): void {
    this.editing.set(null);
  }

  protected async saveTitle(event?: Event): Promise<void> {
    event?.preventDefault();
    const title = this.textDraft().trim();
    if (!title) {
      return;
    }
    await this.patch({ title });
  }

  protected async saveDescription(): Promise<void> {
    const description = this.textDraft().trim();
    await this.patch({ description: description || null });
  }

  protected async saveSize(value: string): Promise<void> {
    await this.patch({ size: value || null });
  }

  protected async choosePerson(field: PersonField, person: PersonRef | null): Promise<void> {
    await this.patch({ [field]: person?.id ?? null });
  }

  protected async searchPeople(q: string): Promise<void> {
    const a = this.artifact();
    if (!a) {
      return;
    }
    this.personQuery.set(q);
    const seq = ++this.searchSeq;
    try {
      const people = await this.work.people(a.key, q);
      if (seq === this.searchSeq) {
        this.personResults.set(people);
      }
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    }
  }

  protected titleKey(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.cancelField();
    }
  }

  /** Grava um campo; em conflito de versão, recarrega e pede para refazer. */
  private async patch(change: ArtifactPatch): Promise<void> {
    const a = this.artifact();
    if (!a) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      this.artifact.set(await this.work.updateArtifact(a.key, change, a.version));
      this.editing.set(null);
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.code === 'VERSION_CONFLICT') {
        this.editing.set(null);
        this.artifact.set(await this.work.artifact(a.key));
        this.fieldNotice.set('Alguém alterou este item. Os dados foram atualizados; refaça a alteração.');
      } else {
        this.error.set(messageOf(apiError));
      }
    } finally {
      this.busy.set(false);
    }
  }

  protected back(): void {
    this.location.back();
  }

  /** Troca de aba: com alterações não salvas, pergunta antes (#82). */
  protected goTab(event: Event, type: DocType | null): void {
    event.preventDefault();
    if (type === this.docType()) {
      return;
    }
    if (!this.canLeave() && !window.confirm(LEAVE_MESSAGE)) {
      return;
    }
    this.stopEditing();
    void this.router.navigate([], { queryParams: { tab: type } });
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

  private draftId(): string | null {
    const doc = this.document();
    return doc ? draftKey('doc', `${doc.artifactKey}.${doc.type}`) : null;
  }

  protected summaryOf(type: DocType): ArtifactDocSummary | undefined {
    return this.summaries().find((s) => s.type === type);
  }

  protected startEdit(): void {
    const doc = this.document();
    if (!doc) {
      return;
    }
    this.draft.set(doc.sections.map((s) => ({ key: s.key, title: s.title, required: s.required, doc: blocksToDoc(s.blocks) })));
    this.draftSummary.set('');
    this.conflict.set(false);
    this.error.set(null);
    this.dirty.set(false);
    this.draftSavedAt.set(null);
    this.draftFound.set(null);
    const id = this.draftId();
    const stored = id ? this.drafts.read(id) : null;
    const match = draftMatch(stored, doc.currentVersion);
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
    const id = this.draftId();
    const stored = id ? this.drafts.read(id) : null;
    if (!stored) {
      return;
    }
    this.draft.set(
      this.draft().map((s) => {
        const blocks = draftBlocks(stored, s.key);
        return blocks ? { ...s, doc: blocksToDoc(blocks) } : s;
      }),
    );
    const found = this.draftFound();
    this.draftFound.set(found ? { ...found, match: 'same' } : null);
    this.dirty.set(true);
    this.draftSavedAt.set(new Date(stored.savedAt));
  }

  protected discardStoredDraft(): void {
    const id = this.draftId();
    if (id) {
      this.drafts.clear(id);
    }
    this.startEdit();
  }

  protected cancelEdit(): void {
    if (this.dirty() && !window.confirm('Descartar as alterações? O rascunho guardado neste navegador também será apagado.')) {
      return;
    }
    const id = this.draftId();
    if (id) {
      this.drafts.clear(id);
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

  private touch(): void {
    this.dirty.set(true);
    if (this.draftTimer) {
      clearTimeout(this.draftTimer);
    }
    this.draftTimer = setTimeout(() => this.flushDraft(), 800);
  }

  private flushDraft(): void {
    this.draftTimer = null;
    const doc = this.document();
    const id = this.draftId();
    if (!doc || !id || this.mode() !== 'edit' || !this.dirty()) {
      return;
    }
    const savedAt = new Date();
    const ok = this.drafts.write(id, {
      baseVersion: doc.currentVersion,
      sections: this.draft().map((s) => ({ key: s.key, blocks: docToBlocks(s.doc) })),
      savedAt: savedAt.toISOString(),
    });
    if (ok) {
      this.draftSavedAt.set(savedAt);
    }
  }

  protected setDoc(index: number, doc: RichDoc): void {
    this.draft.set(this.draft().map((s, i) => (i === index ? { ...s, doc } : s)));
    this.touch();
  }

  protected async save(): Promise<void> {
    const doc = this.document();
    if (!doc) {
      return;
    }
    await this.run(async () => {
      try {
        const sections = this.draft().map((s) => ({ key: s.key, blocks: docToBlocks(s.doc) }));
        this.document.set(await this.documents.save(doc.artifactKey, doc.type, doc.currentVersion, sections, this.draftSummary().trim()));
        this.drafts.clear(draftKey('doc', `${doc.artifactKey}.${doc.type}`));
        this.stopEditing();
        await this.reloadSummaries();
      } catch (error) {
        if (toApiError(error).code === 'VERSION_CONFLICT') {
          this.conflict.set(true);
          return;
        }
        throw error;
      }
    });
  }

  protected async approve(): Promise<void> {
    const doc = this.document();
    if (doc) {
      await this.run(async () => {
        this.document.set(await this.documents.changeStatus(doc.artifactKey, doc.type, 'APPROVED'));
        await this.reloadSummaries();
      });
    }
  }

  protected async reopen(event: Event): Promise<void> {
    event.preventDefault();
    const doc = this.document();
    if (doc && this.reopenComment().trim()) {
      await this.run(async () => {
        this.document.set(await this.documents.changeStatus(doc.artifactKey, doc.type, 'IN_PROGRESS', this.reopenComment().trim()));
        this.reopening.set(false);
        this.reopenComment.set('');
        await this.reloadSummaries();
      });
    }
  }

  protected async showHistory(): Promise<void> {
    const doc = this.document();
    if (doc) {
      await this.run(async () => {
        this.versions.set(await this.documents.versions(doc.artifactKey, doc.type));
        this.mode.set('history');
      });
    }
  }

  protected async openVersion(number: number): Promise<void> {
    const doc = this.document();
    if (doc) {
      await this.run(async () => {
        this.oldVersion.set(await this.documents.version(doc.artifactKey, doc.type, number));
        this.mode.set('read');
      });
    }
  }

  protected async reloadAfterConflict(): Promise<void> {
    const doc = this.document();
    if (doc) {
      await this.openDocument(doc.artifactKey, doc.type);
    }
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  private async reloadSummaries(): Promise<void> {
    const artifact = this.artifact();
    if (artifact) {
      this.summaries.set((await this.documents.list(artifact.key)).items);
    }
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
    } catch (error) {
      this.error.set(messageOf(toApiError(error)));
    } finally {
      this.busy.set(false);
    }
  }
}
