import { DatePipe, Location } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
  imports: [RouterLink, DatePipe, RichEditor],
  templateUrl: './artifact-page.html',
  styleUrls: ['../wiki/wiki.scss', './artifact-page.scss'],
})
export class ArtifactPage {
  private readonly route = inject(ActivatedRoute);
  private readonly work = inject(WorkService);
  private readonly documents = inject(ArtifactDocumentsService);
  private readonly location = inject(Location);

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

  private async load(key: string): Promise<void> {
    this.loadError.set(null);
    try {
      const artifact = await this.work.artifact(key);
      this.artifact.set(artifact);
      this.summaries.set(TYPES_WITH_DOCUMENTS.includes(artifact.type) ? (await this.documents.list(artifact.key)).items : []);
    } catch (error) {
      this.artifact.set(null);
      this.loadError.set(messageOf(toApiError(error)));
    }
  }

  private async openDocument(key: string, type: DocType): Promise<void> {
    this.mode.set('read');
    this.oldVersion.set(null);
    this.reopening.set(false);
    this.conflict.set(false);
    this.error.set(null);
    try {
      this.document.set(await this.documents.get(key, type));
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
    this.mode.set('edit');
  }

  protected setDoc(index: number, doc: RichDoc): void {
    this.draft.set(this.draft().map((s, i) => (i === index ? { ...s, doc } : s)));
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
        this.mode.set('read');
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
