import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { DOC_TYPE_LABELS, DocType } from '../knowledge/artifact-documents.service';

/** Item aberto, para "Continuar de onde parou" (#83). */
export type VisitType = 'ARTIFACT' | 'DOCUMENT' | 'PAGE';

interface UnitRef {
  id: string;
  name: string | null;
}

/** Item aberto por último (GET /me/recent). */
export interface RecentItem {
  type: VisitType;
  ref: string;
  title: string | null;
  artifactKey: string | null;
  documentType: DocType | null;
  pageId: string | null;
  orgUnit: UnitRef | null;
  visitedAt: string;
}

/** O que mudou nas unidades que a pessoa lê (GET /me/news). */
export interface NewsItem {
  occurredAt: string;
  action: string;
  type: VisitType;
  ref: string;
  title: string | null;
  artifactKey: string | null;
  documentType: DocType | null;
  pageId: string | null;
  orgUnit: UnitRef | null;
  actor: { id: string; name: string | null } | null;
  own: boolean;
}

/** Destino de um item da Home: rota, parâmetros e, para a página do Wiki, a unidade a ativar. */
export interface ItemLink {
  path: string[];
  queryParams: Record<string, string> | null;
  unitId: string | null;
}

type Item = Pick<RecentItem, 'type' | 'title' | 'artifactKey' | 'documentType' | 'pageId' | 'orgUnit'>;

/** Texto do item: "INIT-1 · Título", "PRD de INIT-1 · Título" ou o título da página. */
export function itemLabel(item: Item): string {
  if (item.type === 'PAGE') {
    return item.title ?? 'Página';
  }
  const key = item.artifactKey ?? '';
  const base = item.type === 'DOCUMENT' && item.documentType ? `${DOC_TYPE_LABELS[item.documentType]} de ${key}` : key;
  return item.title ? `${base} · ${item.title}` : base;
}

export function itemLink(item: Item): ItemLink {
  if (item.type === 'PAGE') {
    return { path: ['/wiki'], queryParams: { page: item.pageId ?? '' }, unitId: item.orgUnit?.id ?? null };
  }
  const queryParams = item.type === 'DOCUMENT' && item.documentType ? { tab: item.documentType } : null;
  return { path: ['/artefato', item.artifactKey ?? ''], queryParams, unitId: null };
}

const ACTIONS: Record<string, string> = {
  'artifact.created': 'criou',
  'artifact.updated': 'alterou',
  'artifact.status_changed': 'mudou o status de',
  'artifact.archived': 'arquivou',
  'document.saved': 'salvou uma versão do',
  'document.approved': 'aprovou o',
  'document.reopened': 'reabriu o',
  'knowledge_page.created': 'criou a página',
  'knowledge_page.updated': 'alterou a página',
  'knowledge_page.status_changed': 'mudou o status da página',
  'knowledge_page.moved': 'moveu a página',
  'knowledge_page.archived': 'arquivou a página',
  'knowledge_page.restored': 'restaurou a página',
};

/** Verbo da novidade ("alterou", "aprovou o"...); um evento novo, ainda sem texto, vira "alterou". */
export function actionLabel(action: string): string {
  return ACTIONS[action] ?? 'alterou';
}

/** Quem fez: "Você" para a própria pessoa. */
export function actorLabel(item: NewsItem): string {
  return item.own ? 'Você' : (item.actor?.name ?? 'Alguém');
}

@Injectable({ providedIn: 'root' })
export class ActivityService {
  private readonly http = inject(HttpClient);

  async recent(): Promise<RecentItem[]> {
    return (await firstValueFrom(this.http.get<{ items: RecentItem[] }>('/api/v1/me/recent'))).items;
  }

  async news(): Promise<NewsItem[]> {
    return (await firstValueFrom(this.http.get<{ items: NewsItem[] }>('/api/v1/me/news'))).items;
  }

  /** Avisa que a pessoa abriu o item. Não atrapalha a tela: uma falha aqui só deixa de registrar a visita. */
  visit(type: VisitType, ref: string): void {
    this.http.post('/api/v1/me/recent', { type, ref }).subscribe({ error: () => undefined });
  }
}
