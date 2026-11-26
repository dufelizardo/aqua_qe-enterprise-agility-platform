import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Pessoa citada em um comentário. */
export interface CommentPerson {
  id: string;
  name: string | null;
}

/** Comentário de página (SSD 3.7.1; #85). Excluído com respostas vem sem texto. */
export interface PageComment {
  id: string;
  parentId: string | null;
  author: CommentPerson;
  body: string | null;
  quote: string | null;
  mentions: CommentPerson[];
  createdAt: string;
  deleted: boolean;
  canDelete: boolean;
}

export interface NewComment {
  body: string;
  quote?: string | null;
  parentId?: string | null;
  mentionIds?: string[];
}

/** Trecho do texto de um comentário; `mention` quando é uma @menção a uma pessoa da lista. */
export interface CommentSegment {
  text: string;
  mention: boolean;
}

/** Divide o texto marcando "@Nome" de cada pessoa mencionada (o nome mais longo primeiro). Só texto, sem HTML. */
export function mentionSegments(body: string | null, mentions: CommentPerson[]): CommentSegment[] {
  if (!body) {
    return [];
  }
  const names = mentions
    .map((m) => m.name)
    .filter((n): n is string => !!n)
    .sort((a, b) => b.length - a.length)
    .map((n) => `@${n}`);
  const segments: CommentSegment[] = [];
  let rest = body;
  while (rest.length > 0) {
    let best = -1;
    let found = '';
    for (const name of names) {
      const at = rest.indexOf(name);
      if (at >= 0 && (best < 0 || at < best)) {
        best = at;
        found = name;
      }
    }
    if (best < 0) {
      segments.push({ text: rest, mention: false });
      break;
    }
    if (best > 0) {
      segments.push({ text: rest.slice(0, best), mention: false });
    }
    segments.push({ text: found, mention: true });
    rest = rest.slice(best + found.length);
  }
  return segments;
}

/**
 * Termo da @menção sendo digitada antes do cursor: "@pau" devolve "pau". Nulo quando não há @ aberto (o @ precisa
 * estar no começo ou depois de um espaço, e o termo não tem quebra de linha).
 */
export function mentionQuery(text: string, cursor: number): { start: number; query: string } | null {
  const before = text.slice(0, cursor);
  const at = before.lastIndexOf('@');
  if (at < 0 || (at > 0 && !/\s/.test(before.charAt(at - 1)))) {
    return null;
  }
  const query = before.slice(at + 1);
  if (/[\n@]/.test(query) || query.length > 40) {
    return null;
  }
  return { start: at, query };
}

/** As pessoas escolhidas que continuam citadas no texto (apagar o "@Nome" desfaz a menção). */
export function keptMentions(body: string, chosen: CommentPerson[]): string[] {
  return [...new Set(chosen.filter((p) => p.name && body.includes(`@${p.name}`)).map((p) => p.id))];
}

@Injectable({ providedIn: 'root' })
export class CommentsService {
  private readonly http = inject(HttpClient);

  async list(pageId: string): Promise<PageComment[]> {
    return (await firstValueFrom(this.http.get<{ items: PageComment[] }>(`/api/v1/pages/${pageId}/comments`))).items;
  }

  create(pageId: string, comment: NewComment): Promise<PageComment> {
    return firstValueFrom(this.http.post<PageComment>(`/api/v1/pages/${pageId}/comments`, comment));
  }

  remove(pageId: string, commentId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/v1/pages/${pageId}/comments/${commentId}`));
  }

  async people(pageId: string, q: string): Promise<CommentPerson[]> {
    return (
      await firstValueFrom(this.http.get<{ items: CommentPerson[] }>(`/api/v1/pages/${pageId}/people`, { params: { q } }))
    ).items;
  }
}
