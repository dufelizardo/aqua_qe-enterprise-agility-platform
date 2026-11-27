import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CustomFieldsService, formatValue, keyFromLabel, parseInput, parseOptions } from './custom-fields.service';
import { ruleSummary } from './gates.service';

describe('campos customizados (#17)', () => {
  it('monta a chave a partir do rótulo', () => {
    expect(keyFromLabel('Canal de venda')).toBe('canalDeVenda');
    expect(keyFromLabel('Região atendida')).toBe('regiaoAtendida');
    expect(keyFromLabel('2ª revisão')).toBe('campo2Revisao');
    expect(keyFromLabel('')).toBe('campo');
  });

  it('formata e lê os valores conforme o tipo', () => {
    expect(formatValue({ type: 'BOOLEAN' }, true)).toBe('Sim');
    expect(formatValue({ type: 'BOOLEAN' }, false)).toBe('Não');
    expect(formatValue({ type: 'DATE' }, '2027-03-01')).toBe('01/03/2027');
    expect(formatValue({ type: 'TEXT' }, null)).toBe('—');
    expect(parseInput({ type: 'NUMBER' }, '12,5')).toBe(12.5);
    expect(parseInput({ type: 'BOOLEAN' }, 'false')).toBe(false);
    expect(parseInput({ type: 'TEXT' }, '   ')).toBeNull();
    expect(parseOptions('App, Web\nApp\n Loja ')).toEqual(['App', 'Web', 'Loja']);
  });

  it('resume a regra de campo customizado pela chave', () => {
    expect(ruleSummary({ type: 'REQUIRED_FIELD', params: { field: 'customFields.canal' } })).toBe('Campo: canal');
  });

  it('lista, cria, altera e remove pela API', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const api = TestBed.inject(CustomFieldsService);

    const listed = api.list('STORY');
    http.expectOne('/api/v1/custom-fields?artifactType=STORY').flush({ items: [] });
    expect(await listed).toEqual([]);

    const created = api.create('STORY', { key: 'origem', label: ' Origem ', type: 'TEXT', options: ['x'] });
    const post = http.expectOne('/api/v1/artifact-types/STORY/custom-fields');
    expect(post.request.body).toEqual({ key: 'origem', label: 'Origem', type: 'TEXT', options: [] });
    post.flush({ id: 'f1' });
    await created;

    const updated = api.update('f1', { position: 2 });
    const patch = http.expectOne('/api/v1/custom-fields/f1');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ position: 2 });
    patch.flush({ id: 'f1' });
    await updated;

    const removed = api.remove('f1');
    const del = http.expectOne('/api/v1/custom-fields/f1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
    await removed;
    http.verify();
  });
});
