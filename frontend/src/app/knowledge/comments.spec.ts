import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AppNotification, badgeText, notificationText } from '../layout/notification-bell';
import { CommentsService, keptMentions, mentionQuery, mentionSegments } from './comments.service';

const paula = { id: 'p', name: 'Paula Produto' };
const tiago = { id: 't', name: 'Tiago' };

describe('comentários com @menção (#85)', () => {
  it('acha o termo da @menção sendo digitada', () => {
    expect(mentionQuery('Oi @pau', 7)).toEqual({ start: 3, query: 'pau' });
    expect(mentionQuery('@', 1)).toEqual({ start: 0, query: '' });
    expect(mentionQuery('email@empresa', 13)).toBeNull();
    expect(mentionQuery('Oi @paula\nfim', 13)).toBeNull();
    expect(mentionQuery('sem arroba', 10)).toBeNull();
  });

  it('marca as menções no texto, só das pessoas mencionadas', () => {
    expect(mentionSegments('@Paula Produto e @Tiago, vejam. @Outro', [tiago, paula])).toEqual([
      { text: '@Paula Produto', mention: true },
      { text: ' e ', mention: false },
      { text: '@Tiago', mention: true },
      { text: ', vejam. @Outro', mention: false },
    ]);
    expect(mentionSegments(null, [])).toEqual([]);
  });

  it('apagar o "@Nome" do texto desfaz a menção', () => {
    expect(keptMentions('Oi @Tiago', [paula, tiago, tiago])).toEqual(['t']);
  });

  it('chama a API de comentários e de pessoas', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(CommentsService);

    const created = service.create('pg', { body: 'Oi @Tiago', mentionIds: ['t'] });
    const post = http.expectOne('/api/v1/pages/pg/comments');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ body: 'Oi @Tiago', mentionIds: ['t'] });
    post.flush({ id: 'c1' });
    await created;

    const people = service.people('pg', 'ti');
    const get = http.expectOne((r) => r.url === '/api/v1/pages/pg/people');
    expect(get.request.params.get('q')).toBe('ti');
    get.flush({ items: [tiago] });
    expect(await people).toEqual([tiago]);
    http.verify();
  });
});

describe('notificações (#85)', () => {
  const base: AppNotification = {
    id: 'n', kind: 'MENTION', actor: { id: 'p', name: 'Paula' }, targetType: 'PAGE', targetId: 'pg', orgUnitId: 'u',
    title: 'Guia', excerpt: null, createdAt: '2026-11-26T10:00:00Z', read: false,
  };

  it('frase e contador do sino', () => {
    expect(notificationText(base)).toBe('Paula mencionou você em Guia');
    expect(notificationText({ ...base, kind: 'REPLY', actor: null })).toBe('Alguém respondeu ao seu comentário em Guia');
    expect(badgeText(3)).toBe('3');
    expect(badgeText(12)).toBe('9+');
  });
});
