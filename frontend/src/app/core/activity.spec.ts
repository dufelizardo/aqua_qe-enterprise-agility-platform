import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ActivityService, NewsItem, RecentItem, actionLabel, actorLabel, itemLabel, itemLink } from './activity.service';

const unit = { id: 'u1', name: 'Portfólio' };
const artifact: RecentItem = {
  type: 'ARTIFACT', ref: 'INIT-1', title: 'Checkout', artifactKey: 'INIT-1', documentType: null, pageId: null,
  orgUnit: unit, visitedAt: '2026-11-25T20:00:00Z',
};
const document: RecentItem = { ...artifact, type: 'DOCUMENT', ref: 'INIT-1/UX', documentType: 'UX' };
const page: RecentItem = {
  ...artifact, type: 'PAGE', ref: 'p1', title: 'Guia', artifactKey: null, pageId: 'p1',
};

describe('Home: recentes e novidades (#83)', () => {
  it('o texto do item mostra a chave, o documento e o título', () => {
    expect(itemLabel(artifact)).toBe('INIT-1 · Checkout');
    expect(itemLabel(document)).toBe('UX/UI de INIT-1 · Checkout');
    expect(itemLabel(page)).toBe('Guia');
    expect(itemLabel({ ...document, title: null })).toBe('UX/UI de INIT-1');
  });

  it('o artefato e o documento abrem na página do artefato; a página do Wiki, na unidade dela', () => {
    expect(itemLink(artifact)).toEqual({ path: ['/artefato', 'INIT-1'], queryParams: null, unitId: null });
    expect(itemLink(document)).toEqual({ path: ['/artefato', 'INIT-1'], queryParams: { tab: 'UX' }, unitId: null });
    expect(itemLink(page)).toEqual({ path: ['/wiki'], queryParams: { page: 'p1' }, unitId: 'u1' });
  });

  it('a novidade diz quem fez e o quê; evento sem texto vira "alterou"', () => {
    const news: NewsItem = {
      ...artifact, occurredAt: '2026-11-25T20:00:00Z', action: 'artifact.status_changed',
      actor: { id: 'a', name: 'Paula' }, own: false,
    };
    expect(actorLabel(news)).toBe('Paula');
    expect(actorLabel({ ...news, own: true })).toBe('Você');
    expect(actionLabel(news.action)).toBe('mudou o status de');
    expect(actionLabel('outro.evento')).toBe('alterou');
  });

  it('registra a visita e lê as listas', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ActivityService);
    const http = TestBed.inject(HttpTestingController);

    service.visit('DOCUMENT', 'INIT-1/PRD');
    const post = http.expectOne('/api/v1/me/recent');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ type: 'DOCUMENT', ref: 'INIT-1/PRD' });
    post.flush(null, { status: 404, statusText: 'Not Found' });

    const recent = service.recent();
    http.expectOne('/api/v1/me/recent').flush({ items: [artifact] });
    expect(await recent).toEqual([artifact]);
    const news = service.news();
    http.expectOne('/api/v1/me/news').flush({ items: [] });
    expect(await news).toEqual([]);
    http.verify();
  });
});
