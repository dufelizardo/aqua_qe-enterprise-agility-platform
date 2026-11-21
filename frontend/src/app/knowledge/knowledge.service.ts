import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

const API = '/api/v1';

/** Bloco do conteúdo (SSD 6; REV-05): o contrato é independente do editor. */
export type Block =
  | { type: 'paragraph'; text: string }
  | { type: 'heading'; text: string; level: 2 | 3 }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'table'; rows: string[][] }
  | { type: 'code'; text: string; language?: string }
  | { type: 'callout'; text: string; tone?: 'info' | 'warning' }
  | { type: 'quote'; text: string };

export type BlockType = Block['type'];

export interface PageSection {
  key: string;
  title: string;
  required: boolean;
  blocks: Block[];
}

export type PageStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'PUBLISHED';

/** Item da lista de páginas de uma unidade (GET /org-units/{id}/pages). */
export interface PageSummary {
  id: string;
  parentId: string | null;
  title: string;
  type: 'PAGE' | 'REGIMENTO';
  governed: boolean;
  status: PageStatus;
  publishedVersion: number | null;
  archived: boolean;
  updatedAt: string;
}

export interface PageList {
  orgUnitId: string;
  /** FULL: lê tudo (regras 1 a 7); PUBLISHED: só o publicado (regra 9). */
  access: 'FULL' | 'PUBLISHED';
  canEdit: boolean;
  items: PageSummary[];
}

export interface Page {
  id: string;
  orgUnitId: string;
  parentId: string | null;
  title: string;
  type: 'PAGE' | 'REGIMENTO';
  templateName: string;
  governed: boolean;
  approverRole: string | null;
  status: PageStatus;
  currentVersion: number;
  approvedVersion: number | null;
  publishedVersion: number | null;
  versionNumber: number;
  summary: string | null;
  author: { id: string; name: string } | null;
  savedAt: string;
  archived: boolean;
  archiveReason: string | null;
  access: 'FULL' | 'PUBLISHED';
  canEdit: boolean;
  canApprove: boolean;
  missingSections: string[];
  sections: PageSection[];
}

export interface PageVersion {
  number: number;
  summary: string | null;
  author: { id: string; name: string } | null;
  savedAt: string;
  approved: boolean;
  published: boolean;
}

/** Páginas de conhecimento por unidade (Proposta 07, Fase A; SSD 3.7.1). */
@Injectable({ providedIn: 'root' })
export class KnowledgeService {
  private readonly http = inject(HttpClient);

  pages(orgUnitId: string, archived = false): Promise<PageList> {
    return firstValueFrom(
      this.http.get<PageList>(`${API}/org-units/${orgUnitId}/pages`, { params: { archived: String(archived) } }),
    );
  }

  create(orgUnitId: string, title: string, type: 'PAGE' | 'REGIMENTO', parentId: string | null): Promise<Page> {
    return firstValueFrom(this.http.post<Page>(`${API}/org-units/${orgUnitId}/pages`, { title, type, parentId }));
  }

  page(id: string): Promise<Page> {
    return firstValueFrom(this.http.get<Page>(`${API}/pages/${id}`));
  }

  save(id: string, baseVersion: number, sections: PageSection[], summary: string): Promise<Page> {
    return firstValueFrom(
      this.http.put<Page>(`${API}/pages/${id}`, {
        baseVersion,
        summary: summary || null,
        sections: sections.map((s) => ({ key: s.key, blocks: s.blocks })),
      }),
    );
  }

  update(id: string, changes: { title?: string; parentId?: string | null }): Promise<Page> {
    return firstValueFrom(this.http.patch<Page>(`${API}/pages/${id}`, changes));
  }

  versions(id: string): Promise<PageVersion[]> {
    return firstValueFrom(this.http.get<{ items: PageVersion[] }>(`${API}/pages/${id}/versions`)).then((r) => r.items);
  }

  version(id: string, number: number): Promise<Page> {
    return firstValueFrom(this.http.get<Page>(`${API}/pages/${id}/versions/${number}`));
  }

  changeStatus(id: string, status: PageStatus, comment?: string): Promise<Page> {
    return firstValueFrom(this.http.post<Page>(`${API}/pages/${id}/status`, { status, comment: comment || null }));
  }

  archive(id: string, reason: string): Promise<Page> {
    return firstValueFrom(this.http.post<Page>(`${API}/pages/${id}/archive`, { reason: reason || null }));
  }

  restore(id: string): Promise<Page> {
    return firstValueFrom(this.http.post<Page>(`${API}/pages/${id}/restore`, {}));
  }
}

/** Página na árvore, com a profundidade. Página cujo pai não aparece (não publicado, arquivado) sobe para a raiz. */
export interface PageNode extends PageSummary {
  depth: number;
}

export function pageTree(items: PageSummary[]): PageNode[] {
  const ids = new Set(items.map((p) => p.id));
  const children = new Map<string | null, PageSummary[]>();
  for (const page of items) {
    const parent = page.parentId && ids.has(page.parentId) ? page.parentId : null;
    children.set(parent, [...(children.get(parent) ?? []), page]);
  }
  const result: PageNode[] = [];
  const visit = (parent: string | null, depth: number) => {
    for (const page of children.get(parent) ?? []) {
      result.push({ ...page, depth });
      visit(page.id, depth + 1);
    }
  };
  visit(null, 0);
  return result;
}

/** Trecho de texto com Markdown leve: **negrito**, *itálico*, `código` e [link](endereço). */
export interface Inline {
  kind: 'text' | 'strong' | 'em' | 'code' | 'link';
  text: string;
  href?: string;
}

const INLINE = /\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)/g;

/**
 * Interpreta o Markdown leve em trechos, sem gerar HTML: a tela monta os elementos, então nada do texto vira marcação.
 * Links só com http(s), mailto ou caminho interno; os demais ficam como texto.
 */
export function parseInline(text: string): Inline[] {
  const result: Inline[] = [];
  let last = 0;
  for (const match of text.matchAll(INLINE)) {
    const index = match.index ?? 0;
    if (index > last) {
      result.push({ kind: 'text', text: text.slice(last, index) });
    }
    if (match[1] !== undefined) {
      result.push({ kind: 'strong', text: match[1] });
    } else if (match[2] !== undefined) {
      result.push({ kind: 'em', text: match[2] });
    } else if (match[3] !== undefined) {
      result.push({ kind: 'code', text: match[3] });
    } else if (match[4] !== undefined && match[5] !== undefined) {
      const href = match[5];
      result.push(safeHref(href) ? { kind: 'link', text: match[4], href } : { kind: 'text', text: match[0] });
    }
    last = index + match[0].length;
  }
  if (last < text.length) {
    result.push({ kind: 'text', text: text.slice(last) });
  }
  return result;
}

function safeHref(href: string): boolean {
  return /^(https?:\/\/|mailto:|\/(?!\/))/i.test(href);
}

/** Bloco vazio de cada tipo, para o editor. */
export function emptyBlock(type: BlockType): Block {
  switch (type) {
    case 'heading':
      return { type, text: '', level: 2 };
    case 'list':
      return { type, ordered: false, items: [''] };
    case 'table':
      return { type, rows: [['', ''], ['', '']] };
    case 'code':
      return { type, text: '' };
    case 'callout':
      return { type, text: '', tone: 'info' };
    default:
      return { type, text: '' } as Block;
  }
}

/** Rótulos dos status do ciclo governado (KNOW-D05). */
export const STATUS_LABELS: Record<PageStatus, string> = {
  DRAFT: 'Rascunho',
  IN_REVIEW: 'Em revisão',
  APPROVED: 'Aprovado',
  PUBLISHED: 'Publicado',
};
