import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ItemLink } from './activity.service';

export type HitKind = 'ARTIFACT' | 'DOCUMENT' | 'PAGE';

/** Um resultado de GET /search (SSD 3.10; #84). */
export interface SearchHit {
  kind: HitKind;
  title: string | null;
  /** Artefato e documento: a chave do artefato. */
  key: string | null;
  documentType: string | null;
  pageId: string | null;
  /** Artefato: o tipo (INITIATIVE...); página: PAGE ou REGIMENTO. */
  type: string | null;
  status: string | null;
  orgUnit: { id: string; name: string | null } | null;
  snippet: string | null;
  updatedAt: string | null;
}

export interface SearchResults {
  q: string;
  artifacts: SearchHit[];
  documents: SearchHit[];
  pages: SearchHit[];
}

/** Trecho de texto, marcado quando é um termo da busca. */
export interface Segment {
  text: string;
  mark: boolean;
}

/** Minúsculas e sem acento, caractere por caractere: as posições do texto dobrado são as do original. */
export function foldChars(text: string): string {
  return Array.from(text, (c) => {
    const folded = c.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '');
    return folded.length === 1 ? folded : c.toLowerCase().charAt(0);
  }).join('');
}

/**
 * Divide o texto em trechos, marcando cada ocorrência dos termos sem diferença de maiúsculas e acentos, como a busca
 * do servidor. Só texto: nada vira HTML.
 */
export function highlight(text: string | null, query: string): Segment[] {
  if (!text) {
    return [];
  }
  const chars = Array.from(text);
  const folded = foldChars(text);
  const foldedChars = Array.from(folded);
  const terms = foldChars(query).split(/\s+/).filter((t) => t.length > 0);
  const marked = new Array<boolean>(chars.length).fill(false);
  const haystack = foldedChars.join('');
  for (const term of terms) {
    let from = 0;
    for (;;) {
      const at = haystack.indexOf(term, from);
      if (at < 0) {
        break;
      }
      // haystack tem um caractere por posição de `chars` (foldChars preserva o tamanho em code points).
      const start = Array.from(haystack.slice(0, at)).length;
      const length = Array.from(term).length;
      for (let i = start; i < start + length && i < marked.length; i++) {
        marked[i] = true;
      }
      from = at + term.length;
    }
  }
  const segments: Segment[] = [];
  chars.forEach((c, i) => {
    const last = segments[segments.length - 1];
    if (last && last.mark === marked[i]) {
      last.text += c;
    } else {
      segments.push({ text: c, mark: marked[i]! });
    }
  });
  return segments;
}

const DOC_LABELS: Record<string, string> = { PRD: 'PRD', SAD: 'SAD', SSD: 'SSD', UX: 'UX/UI', QA: 'QA' };

/** Rótulo curto do tipo do resultado. */
export function hitBadge(hit: SearchHit): string {
  if (hit.kind === 'DOCUMENT') {
    return DOC_LABELS[hit.documentType ?? ''] ?? hit.documentType ?? 'Documento';
  }
  if (hit.kind === 'PAGE') {
    return hit.type === 'REGIMENTO' ? 'Regimento' : 'Página';
  }
  return hit.key ?? 'Artefato';
}

/** Para onde o resultado leva: o artefato (na aba do documento) ou a página do Wiki/Regimento na unidade dela. */
export function hitLink(hit: SearchHit): ItemLink {
  if (hit.kind === 'PAGE') {
    const regimento = hit.type === 'REGIMENTO';
    return {
      path: [regimento ? '/regimento' : '/wiki'],
      queryParams: regimento ? null : { page: hit.pageId ?? '' },
      unitId: hit.orgUnit?.id ?? null,
    };
  }
  const queryParams = hit.kind === 'DOCUMENT' && hit.documentType ? { tab: hit.documentType } : null;
  return { path: ['/artefato', hit.key ?? ''], queryParams, unitId: null };
}

@Injectable({ providedIn: 'root' })
export class SearchService {
  private readonly http = inject(HttpClient);

  search(q: string): Promise<SearchResults> {
    return firstValueFrom(this.http.get<SearchResults>('/api/v1/search', { params: { q } }));
  }
}
