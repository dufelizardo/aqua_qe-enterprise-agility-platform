import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BoardTemplatesService, columnsSummary } from './board-templates.service';
import { WorkService } from './work.service';

describe('templates de quadro (#50)', () => {
  it('resume as colunas com o WIP', () => {
    expect(
      columnsSummary({
        columns: [
          { name: 'Triagem', phase: 'UPSTREAM', statusKeys: ['OPEN'], wipLimit: null, wipPolicy: 'WARN' },
          { name: 'Em atendimento', phase: 'DOWNSTREAM', statusKeys: ['FIXING'], wipLimit: 5, wipPolicy: 'WARN' },
        ],
      }),
    ).toBe('Triagem → Em atendimento (WIP 5)');
  });

  it('lista, salva a partir do quadro, renomeia, remove e cria quadro com template', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const api = TestBed.inject(BoardTemplatesService);
    const work = TestBed.inject(WorkService);

    const listed = api.list('FL1');
    http.expectOne('/api/v1/board-templates?level=FL1').flush({ items: [] });
    expect(await listed).toEqual([]);

    const saved = api.saveFromBoard('b1', '  Do time ', '  ');
    const post = http.expectOne('/api/v1/boards/b1/template');
    expect(post.request.body).toEqual({ name: 'Do time', description: null });
    post.flush({ id: 't1' });
    await saved;

    const updated = api.update('t1', 'Novo', 'Descrição');
    const patch = http.expectOne('/api/v1/board-templates/t1');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ name: 'Novo', description: 'Descrição' });
    patch.flush({ id: 't1' });
    await updated;

    const removed = api.remove('t1');
    const del = http.expectOne('/api/v1/board-templates/t1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    await removed;

    const withTemplate = work.createBoard('u1', 'FL1', 't1');
    const create = http.expectOne('/api/v1/boards');
    expect(create.request.body).toEqual({ orgUnitId: 'u1', level: 'FL1', templateId: 't1' });
    create.flush({ id: 'b2' });
    await withTemplate;

    const plain = work.createBoard('u1', 'FL1', null);
    expect(http.expectOne('/api/v1/boards').request.body).toEqual({ orgUnitId: 'u1', level: 'FL1' });
    http.verify();
  });
});
