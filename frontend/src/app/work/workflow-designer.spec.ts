import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  WorkflowDesignerService,
  WorkflowVersion,
  draftOf,
  keyFromName,
  removeStatus,
  removedKeys,
  versionBody,
} from './workflow-designer.service';

const published: WorkflowVersion = {
  id: 'w1',
  familyId: 'w1',
  name: 'Fluxo padrão de Feature',
  artifactTypes: ['FEATURE'],
  versionNumber: 1,
  active: true,
  orgUnit: { id: 'root', name: 'Empresa' },
  publishedAt: '2026-11-27T10:00:00Z',
  statuses: [
    { id: 's3', key: 'CANCELLED', name: 'Cancelado', phase: null, category: 'DONE', position: null },
    { id: 's2', key: 'DOING', name: 'Em andamento', phase: 'DOWNSTREAM', category: 'IN_PROGRESS', position: 2 },
    { id: 's1', key: 'BACKLOG', name: 'Backlog', phase: 'UPSTREAM', category: 'TODO', position: 1 },
  ],
  transitions: [
    { id: 't1', from: 'BACKLOG', to: 'DOING' },
    { id: 't2', from: 'DOING', to: 'CANCELLED' },
  ],
};

describe('Workflow Designer (#49)', () => {
  it('o rascunho põe a sequência em ordem e deixa os de fora no fim', () => {
    const draft = draftOf(published);
    expect(draft.statuses.map((s) => s.key)).toEqual(['BACKLOG', 'DOING', 'CANCELLED']);
    expect(draft.statuses.map((s) => s.inSequence)).toEqual([true, true, false]);
    expect(draft.transitions).toEqual(new Set(['BACKLOG>DOING', 'DOING>CANCELLED']));
  });

  it('a chave do status novo vem do nome, sem acentos e sem repetir', () => {
    expect(keyFromName('Em Validação', [])).toBe('EM_VALIDACAO');
    expect(keyFromName('Em Validação', ['EM_VALIDACAO'])).toBe('EM_VALIDACAO_2');
    expect(keyFromName('2ª revisão', [])).toBe('S_2_REVISAO');
    expect(keyFromName('  !! ', [])).toBe('STATUS');
    expect(keyFromName('x'.repeat(60), []).length).toBe(40);
  });

  it('remover um status tira as transições dele e entra nos removidos', () => {
    const draft = removeStatus(draftOf(published), 'DOING');
    expect(draft.statuses.map((s) => s.key)).toEqual(['BACKLOG', 'CANCELLED']);
    expect(draft.transitions.size).toBe(0);
    expect(removedKeys(published, draft)).toEqual(['DOING']);
  });

  it('o corpo da versão leva a ordem, a sequência e as transições válidas', () => {
    const draft = draftOf(published);
    draft.name = '  Fluxo da Cadeia ';
    draft.transitions.add('BACKLOG>GONE');
    expect(versionBody(draft)).toEqual({
      name: 'Fluxo da Cadeia',
      statuses: [
        { key: 'BACKLOG', name: 'Backlog', phase: 'UPSTREAM', category: 'TODO', inSequence: true },
        { key: 'DOING', name: 'Em andamento', phase: 'DOWNSTREAM', category: 'IN_PROGRESS', inSequence: true },
        { key: 'CANCELLED', name: 'Cancelado', phase: null, category: 'DONE', inSequence: false },
      ],
      transitions: [
        { from: 'BACKLOG', to: 'DOING' },
        { from: 'DOING', to: 'CANCELLED' },
      ],
    });
  });

  it('chama personalizar, publicar, voltar a herdar e migrar', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const api = TestBed.inject(WorkflowDesignerService);

    const custom = api.customize('u1', 'w1');
    const post = http.expectOne('/api/v1/org-units/u1/workflows');
    expect(post.request.body).toEqual({ from: 'w1' });
    post.flush(published);
    await custom;

    const version = api.publish('w1', draftOf(published));
    const publish = http.expectOne('/api/v1/workflows/w1/versions');
    expect(publish.request.method).toBe('POST');
    expect(publish.request.body.statuses.length).toBe(3);
    publish.flush(published);
    await version;

    const migrated = api.migrate('w1', { DOING: 'BACKLOG' });
    const migration = http.expectOne('/api/v1/workflows/w1/migration');
    expect(migration.request.body).toEqual({ mapping: { DOING: 'BACKLOG' } });
    migration.flush({ migrated: 2 });
    expect((await migrated).migrated).toBe(2);

    const reverted = api.revert('w1');
    const del = http.expectOne('/api/v1/workflows/w1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    await reverted;
    http.verify();
  });
});
