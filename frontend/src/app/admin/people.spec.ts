import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PeopleService, confirmsName, personalDataUrl } from './people.service';

describe('Pessoas e privacidade (#32)', () => {
  it('a anonimização só confirma com o nome digitado, sem diferenciar maiúsculas', () => {
    expect(confirmsName('  ana souza ', 'Ana Souza')).toBe(true);
    expect(confirmsName('Ana', 'Ana Souza')).toBe(false);
    expect(confirmsName('', '')).toBe(false);
  });

  it('o endereço dos dados da pessoa e a busca', async () => {
    expect(personalDataUrl('a b')).toBe('/api/v1/admin/people/a%20b/personal-data');
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(PeopleService);
    const http = TestBed.inject(HttpTestingController);

    const found = service.search('ana');
    const request = http.expectOne((r) => r.url === '/api/v1/admin/people' && r.params.get('q') === 'ana');
    request.flush({ items: [{ id: '1', name: 'Ana', username: 'ana', email: 'ana@x', active: true, anonymized: false, lastSeenAt: '2026-11-28T10:00:00Z' }] });
    expect((await found)[0].email).toBe('ana@x');

    const done = service.anonymize('1');
    const post = http.expectOne('/api/v1/admin/people/1/anonymize');
    expect(post.request.method).toBe('POST');
    post.flush({ id: '1', name: 'Pessoa removida (0000)', active: false, anonymized: true });
    expect((await done).anonymized).toBe(true);
    http.verify();
  });
});
