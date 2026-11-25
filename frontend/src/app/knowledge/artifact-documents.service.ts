import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Block, PageSection, blocksToDoc, docHasText } from './knowledge.service';

const API = '/api/v1';

/** Tipos de documento da Iniciativa e da Feature, na ordem das abas (SSD 3.7). */
export const DOC_TYPES = ['PRD', 'SAD', 'SSD', 'UX', 'QA'] as const;
export type DocType = (typeof DOC_TYPES)[number];
export const DOC_TYPE_LABELS: Record<DocType, string> = { PRD: 'PRD', SAD: 'SAD', SSD: 'SSD', UX: 'UX/UI', QA: 'QA' };

/** Tipos de artefato que têm documentos (SSD 3.7). */
export const TYPES_WITH_DOCUMENTS = ['INITIATIVE', 'FEATURE'];

export type DocStatus = 'PENDING' | 'IN_PROGRESS' | 'APPROVED';
export const DOC_STATUS_LABELS: Record<DocStatus, string> = {
  PENDING: 'Pendente',
  IN_PROGRESS: 'Em andamento',
  APPROVED: 'Aprovado',
};

export interface ArtifactDocSummary {
  type: DocType;
  name: string;
  status: DocStatus;
  currentVersion: number;
  approvedVersion: number | null;
  updatedAt: string | null;
  canEdit: boolean;
  canApprove: boolean;
}

export interface ArtifactDocument {
  artifactKey: string;
  type: DocType;
  name: string;
  approverRole: string | null;
  status: DocStatus;
  currentVersion: number;
  approvedVersion: number | null;
  versionNumber: number;
  summary: string | null;
  author: { id: string; name: string } | null;
  savedAt: string | null;
  canEdit: boolean;
  canApprove: boolean;
  missingSections: string[];
  sections: PageSection[];
}

export interface ArtifactDocVersion {
  number: number;
  summary: string | null;
  author: { id: string; name: string } | null;
  savedAt: string;
  approved: boolean;
}

/** Linha do índice de seções: obrigatória (*) e preenchida (UX/UI 6.6). */
export interface SectionIndexItem {
  key: string;
  title: string;
  required: boolean;
  filled: boolean;
}

export function sectionIndex(sections: PageSection[]): SectionIndexItem[] {
  return sections.map((s) => ({
    key: s.key,
    title: s.title,
    required: s.required,
    filled: docHasText(blocksToDoc(s.blocks)),
  }));
}

/** Documentos da Iniciativa e da Feature (SSD 3.7). */
@Injectable({ providedIn: 'root' })
export class ArtifactDocumentsService {
  private readonly http = inject(HttpClient);

  list(artifact: string): Promise<{ artifactKey: string; items: ArtifactDocSummary[] }> {
    return firstValueFrom(
      this.http.get<{ artifactKey: string; items: ArtifactDocSummary[] }>(`${API}/artifacts/${artifact}/documents`),
    );
  }

  get(artifact: string, type: DocType): Promise<ArtifactDocument> {
    return firstValueFrom(this.http.get<ArtifactDocument>(`${API}/artifacts/${artifact}/documents/${type}`));
  }

  save(artifact: string, type: DocType, baseVersion: number, sections: { key: string; blocks: Block[] }[], summary: string): Promise<ArtifactDocument> {
    return firstValueFrom(
      this.http.put<ArtifactDocument>(`${API}/artifacts/${artifact}/documents/${type}`, {
        baseVersion,
        summary: summary || null,
        sections,
      }),
    );
  }

  changeStatus(artifact: string, type: DocType, status: 'APPROVED' | 'IN_PROGRESS', comment?: string): Promise<ArtifactDocument> {
    return firstValueFrom(
      this.http.post<ArtifactDocument>(`${API}/artifacts/${artifact}/documents/${type}/status`, { status, comment }),
    );
  }

  versions(artifact: string, type: DocType): Promise<ArtifactDocVersion[]> {
    return firstValueFrom(this.http.get<ArtifactDocVersion[]>(`${API}/artifacts/${artifact}/documents/${type}/versions`));
  }

  version(artifact: string, type: DocType, number: number): Promise<ArtifactDocument> {
    return firstValueFrom(
      this.http.get<ArtifactDocument>(`${API}/artifacts/${artifact}/documents/${type}/versions/${number}`),
    );
  }
}
