import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { gateErrorText } from '../admin/admin-gates';
import { GatesService, RULE_LABELS, approversText, RuleType, emptyParams, ruleSummary } from './gates.service';

describe('gates (#78)', () => {
  it('cada tipo de regra tem rótulo e parâmetros iniciais', () => {
    for (const type of Object.keys(RULE_LABELS) as RuleType[]) {
      expect(emptyParams(type)).toBeTruthy();
    }
    expect(emptyParams('MIN_CHILDREN')).toEqual({ childType: 'FEATURE', min: 1 });
  });

  it('resumo legível de cada regra', () => {
    expect(ruleSummary({ type: 'REQUIRED_FIELD', params: { field: 'size' } })).toBe('Campo: Estimativa');
    expect(ruleSummary({ type: 'DOCUMENT_APPROVED', params: { documentType: 'PRD' } })).toBe('PRD aprovado');
    expect(ruleSummary({ type: 'DOCUMENT_SECTION_FILLED', params: { documentType: 'SAD', sectionKey: 'decisions' } }))
      .toBe('SAD: seção decisions');
    expect(ruleSummary({ type: 'MIN_CHILDREN', params: { childType: 'FEATURE', min: 2 } })).toBe('Ao menos 2 FEATURE');
    expect(ruleSummary({ type: 'ROLE_APPROVAL', params: { role: 'TECH_LEAD', label: 'Architecture Gate' } }))
      .toBe('Architecture Gate (Tech Lead)');
    expect(ruleSummary({ type: 'ROLE_APPROVAL', params: { role: 'QA_ENGINEER' } })).toBe('Aprovação de QA');
  });

  it('o erro ao salvar aponta a regra que falhou', () => {
    expect(
      gateErrorText({
        status: 400,
        code: 'VALIDATION_FAILED',
        detail: 'x',
        errors: [{ field: 'rules[1].params.sectionKey', message: 'o PRD não tem essa seção' }],
      }),
    ).toBe('Regra 2: o PRD não tem essa seção');
  });

  it('chama o painel, a decisão e a configuração', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const service = TestBed.inject(GatesService);

    const decided = service.decide('INIT-1', 'r1', 'REJECTED', 'Falta o diagrama.');
    const post = http.expectOne('/api/v1/artifacts/INIT-1/approvals');
    expect(post.request.body).toEqual({ gateRuleId: 'r1', decision: 'REJECTED', comment: 'Falta o diagrama.' });
    post.flush({ items: [] });
    expect(await decided).toEqual([]);

    const saved = service.save('t1', 'Pronto', [{ type: 'DOCUMENT_APPROVED', params: { documentType: 'PRD' }, message: 'm' }]);
    const put = http.expectOne('/api/v1/workflow-transitions/t1/gate');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.name).toBe('Pronto');
    put.flush({ id: 'g' });
    await saved;
    http.verify();
  });

  it('quem pode aprovar aparece por nome, com o total acima de 10 (#14)', () => {
    const p = (name: string) => ({ id: name, name });
    expect(approversText({ approvers: null })).toBe('');
    expect(approversText({ approvers: [], approverCount: 0 })).toContain('Ninguém tem este papel');
    expect(approversText({ approvers: [p('Ana')], approverCount: 1 })).toBe('Pode aprovar: Ana');
    expect(approversText({ approvers: [p('Ana'), p('Bruno'), p('Caio')], approverCount: 3 })).toBe(
      'Pode aprovar: Ana, Bruno e Caio',
    );
    expect(approversText({ approvers: [p('Ana'), p('Bruno')], approverCount: 7 })).toBe(
      'Pode aprovar: Ana, Bruno e mais 5',
    );
  });
});
