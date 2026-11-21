import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

const API = '/api/v1';

/**
 * Bloco do conteúdo (SSD 6). Desde o bug #67, cada seção é editada num editor de texto contínuo (Tiptap/ProseMirror)
 * e gravada como um único bloco `richtext`, com o documento do editor. Os demais tipos são do editor anterior: continuam
 * legíveis e são convertidos ao abrir ({@link blocksToDoc}).
 */
export type Block =
  | { type: 'richtext'; doc: RichDoc }
  | { type: 'paragraph'; text: string }
  | { type: 'heading'; text: string; level: 2 | 3 }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'table'; rows: string[][] }
  | { type: 'code'; text: string; language?: string }
  | { type: 'callout'; text: string; tone?: 'info' | 'warning' }
  | { type: 'quote'; text: string };

export type BlockType = Block['type'];

/** Nó do documento do editor (JSON do ProseMirror). */
export interface RichNode {
  type: string;
  attrs?: Record<string, unknown>;
  content?: RichNode[];
  marks?: { type: string; attrs?: Record<string, unknown> }[];
  text?: string;
}

export interface RichDoc extends RichNode {
  type: 'doc';
}

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

  create(orgUnitId: string, title: string, parentId: string | null): Promise<Page> {
    return firstValueFrom(this.http.post<Page>(`${API}/org-units/${orgUnitId}/pages`, { title, parentId }));
  }

  /** Regimento da unidade (independente do Wiki): o ativo, ou nulo, e se o usuário pode criá-lo. */
  regimento(orgUnitId: string): Promise<{ access: 'FULL' | 'PUBLISHED'; canEdit: boolean; regimento: Page | null }> {
    return firstValueFrom(
      this.http.get<{ access: 'FULL' | 'PUBLISHED'; canEdit: boolean; regimento: Page | null }>(
        `${API}/org-units/${orgUnitId}/regimento`,
      ),
    );
  }

  createRegimento(orgUnitId: string, title: string): Promise<Page> {
    return firstValueFrom(this.http.post<Page>(`${API}/org-units/${orgUnitId}/regimento`, { title }));
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

/** Rótulos dos status do ciclo governado (KNOW-D05). */
export const STATUS_LABELS: Record<PageStatus, string> = {
  DRAFT: 'Rascunho',
  IN_REVIEW: 'Em revisão',
  APPROVED: 'Aprovado',
  PUBLISHED: 'Publicado',
};

/** Documento vazio do editor. */
export function emptyDoc(): RichDoc {
  return { type: 'doc', content: [{ type: 'paragraph' }] };
}

/**
 * Converte os blocos de uma seção no documento do editor (bug #67). O bloco `richtext` já é o documento; os blocos do
 * editor anterior viram os nós equivalentes, com o Markdown leve virando marcas (negrito, itálico, código, link). Nada se
 * perde: o que o editor anterior gravou continua legível e passa a ser editável.
 */
export function blocksToDoc(blocks: Block[] | null | undefined): RichDoc {
  const content: RichNode[] = [];
  for (const block of blocks ?? []) {
    switch (block.type) {
      case 'richtext':
        content.push(...(block.doc?.content ?? []));
        break;
      case 'heading':
        content.push({ type: 'heading', attrs: { level: block.level === 3 ? 3 : 2 }, content: inline(block.text) });
        break;
      case 'list':
        content.push({
          type: block.ordered ? 'orderedList' : 'bulletList',
          content: block.items
            .filter((item) => item.trim())
            .map((item) => ({ type: 'listItem', content: [{ type: 'paragraph', content: inline(item) }] })),
        });
        break;
      case 'table':
        content.push({
          type: 'table',
          content: block.rows.map((row, r) => ({
            type: 'tableRow',
            content: row.map((cell) => ({
              type: r === 0 ? 'tableHeader' : 'tableCell',
              content: [{ type: 'paragraph', content: inline(cell.trim()) }],
            })),
          })),
        });
        break;
      case 'code':
        content.push({ type: 'codeBlock', content: block.text ? [{ type: 'text', text: block.text }] : undefined });
        break;
      case 'callout':
      case 'quote':
        content.push({ type: 'blockquote', content: [{ type: 'paragraph', content: inline(block.text) }] });
        break;
      default:
        content.push({ type: 'paragraph', content: inline((block as { text?: string }).text ?? '') });
    }
  }
  return { type: 'doc', content: content.length > 0 ? content.map(clean) : [{ type: 'paragraph' }] };
}

/** O documento do editor como blocos da seção, para gravar. */
export function docToBlocks(doc: RichDoc): Block[] {
  return [{ type: 'richtext', doc }];
}

/** A seção tem texto? (o mesmo critério do servidor para as seções obrigatórias). */
export function docHasText(doc: RichNode | undefined): boolean {
  if (!doc) {
    return false;
  }
  return (doc.text?.trim().length ?? 0) > 0 || (doc.content ?? []).some(docHasText);
}

function inline(text: string): RichNode[] | undefined {
  const nodes: RichNode[] = parseInline(text ?? '')
    .filter((part) => part.text.length > 0)
    .map((part) => {
      switch (part.kind) {
        case 'strong':
          return { type: 'text', text: part.text, marks: [{ type: 'bold' }] };
        case 'em':
          return { type: 'text', text: part.text, marks: [{ type: 'italic' }] };
        case 'code':
          return { type: 'text', text: part.text, marks: [{ type: 'code' }] };
        case 'link':
          return { type: 'text', text: part.text, marks: [{ type: 'link', attrs: { href: part.href } }] };
        default:
          return { type: 'text', text: part.text };
      }
    });
  return nodes.length > 0 ? nodes : undefined;
}

/** Tira "content" vazio, que o ProseMirror recusa em nós de texto. */
function clean(node: RichNode): RichNode {
  const result: RichNode = { ...node };
  if (result.content) {
    result.content = result.content.map(clean);
  } else {
    delete result.content;
  }
  return result;
}
