import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DefaultOwnerService, OWNER_ROLES, ownerIdToSend, ruleSummary } from './default-owner.service';

describe('responsável padrão (#43)', () => {
  it('resumo legível de cada regra', () => {
    expect(ruleSummary({ mode: 'NONE', role: null })).toBe('Sem responsável');
    expect(ruleSummary({ mode: 'CREATOR', role: null })).toBe('Quem criou');
    expect(ruleSummary({ mode: 'ROLE', role: 'BUSINESS_OWNER' })).toBe('Pessoa com o papel: Business Owner');
  });

  it('o administrador da plataforma não é opção de responsável', () => {
    expect(OWNER_ROLES).not.toContain('PLATFORM_ADMIN');
    expect(OWNER_ROLES).toContain('PRODUCT_MANAGER');
  });

  it('a escolha do formulário vira o ownerId enviado', () => {
    expect(ownerIdToSend('u1')).toBe('u1');
    expect(ownerIdToSend('none')).toBeNull();
    expect(ownerIdToSend('')).toBeUndefined();
  });

  it('chama regras, gravação, remoção e sugestão', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const api = TestBed.inject(DefaultOwnerService);

    const saved = api.save('u1', 'INITIATIVE', 'CREATOR', 'TECH_LEAD');
    const put = http.expectOne('/api/v1/org-units/u1/default-owner-rules/INITIATIVE');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ mode: 'CREATOR' });
    put.flush({ mode: 'CREATOR' });
    await saved;

    const role = api.save('u1', 'FEATURE', 'ROLE', 'TECH_LEAD');
    http.expectOne('/api/v1/org-units/u1/default-owner-rules/FEATURE').flush({});
    await role;

    const removed = api.remove('u1', 'INITIATIVE');
    const del = http.expectOne('/api/v1/org-units/u1/default-owner-rules/INITIATIVE');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    await removed;

    const suggestion = api.suggestion('u1', 'INITIATIVE');
    const get = http.expectOne((r) => r.url === '/api/v1/org-units/u1/default-owner');
    expect(get.request.params.get('artifactType')).toBe('INITIATIVE');
    get.flush({ mode: 'ROLE', role: 'BUSINESS_OWNER', source: null, candidates: [], suggestedId: null });
    expect((await suggestion).mode).toBe('ROLE');

    const rules = api.rules('u1');
    http.expectOne('/api/v1/org-units/u1/default-owner-rules').flush({ items: [] });
    expect(await rules).toEqual([]);
    http.verify();
  });
});
