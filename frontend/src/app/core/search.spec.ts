import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SearchHit, SearchService, foldChars, highlight, hitBadge, hitLink } from './search.service';

const base: SearchHit = {
  kind: 'ARTIFACT', title: 'Governança de Pagamentos', key: 'INIT-7', documentType: null, pageId: null,
  type: 'INITIATIVE', status: 'Ideia', orgUnit: { id: 'u1', name: 'Portfólio' }, snippet: null, updatedAt: null,
};

describe('busca (#84)', () => {
  it('dobra acentos sem mudar as posições', () => {
    expect(foldChars('Governança ÁGIL')).toBe('governanca agil');
    expect(foldChars('Governança').length).toBe('Governança'.length);
  });

  it('marca os termos sem diferença de maiúsculas e acentos', () => {
    expect(highlight('Governança de Pagamentos', 'GOVERNANCA pag')).toEqual([
      { text: 'Governança', mark: true },
      { text: ' de ', mark: false },
      { text: 'Pag', mark: true },
      { text: 'amentos', mark: false },
    ]);
    expect(highlight(null, 'x')).toEqual([]);
    expect(highlight('<b>texto</b>', 'texto')).toEqual([
      { text: '<b>', mark: false },
      { text: 'texto', mark: true },
      { text: '</b>', mark: false },
    ]);
  });

  it('rótulo e destino de cada tipo de resultado', () => {
    expect(hitBadge(base)).toBe('INIT-7');
    expect(hitLink(base)).toEqual({ path: ['/artefato', 'INIT-7'], queryParams: null, unitId: null });
    const doc: SearchHit = { ...base, kind: 'DOCUMENT', documentType: 'UX' };
    expect(hitBadge(doc)).toBe('UX/UI');
    expect(hitLink(doc).queryParams).toEqual({ tab: 'UX' });
    const page: SearchHit = { ...base, kind: 'PAGE', key: null, pageId: 'p1', type: 'PAGE' };
    expect(hitBadge(page)).toBe('Página');
    expect(hitLink(page)).toEqual({ path: ['/wiki'], queryParams: { page: 'p1' }, unitId: 'u1' });
    const regimento: SearchHit = { ...page, type: 'REGIMENTO' };
    expect(hitBadge(regimento)).toBe('Regimento');
    expect(hitLink(regimento)).toEqual({ path: ['/regimento'], queryParams: null, unitId: 'u1' });
  });

  it('chama GET /search com o termo', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const result = TestBed.inject(SearchService).search('governança');
    const req = http.expectOne((r) => r.url === '/api/v1/search');
    expect(req.request.params.get('q')).toBe('governança');
    req.flush({ q: 'governança', artifacts: [base], documents: [], pages: [] });
    expect((await result).artifacts).toEqual([base]);
    http.verify();
  });
});
