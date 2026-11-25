import { DOC_STATUS_LABELS, DOC_TYPES, DOC_TYPE_LABELS, sectionIndex } from './artifact-documents.service';

describe('documentos do artefato (#63)', () => {
  it('as abas seguem a ordem PRD, SAD, SSD, UX/UI e QA', () => {
    expect(DOC_TYPES.map((t) => DOC_TYPE_LABELS[t])).toEqual(['PRD', 'SAD', 'SSD', 'UX/UI', 'QA']);
  });

  it('os status têm rótulo em português', () => {
    expect(DOC_STATUS_LABELS).toEqual({ PENDING: 'Pendente', IN_PROGRESS: 'Em andamento', APPROVED: 'Aprovado' });
  });

  it('o índice marca as seções preenchidas e as obrigatórias', () => {
    const index = sectionIndex([
      { key: 'context', title: 'Contexto', required: true, blocks: [{ type: 'paragraph', text: 'Texto' }] },
      { key: 'objectives', title: 'Objetivos', required: true, blocks: [] },
      { key: 'metrics', title: 'Métricas', required: false, blocks: [{ type: 'paragraph', text: '   ' }] },
    ]);
    expect(index).toEqual([
      { key: 'context', title: 'Contexto', required: true, filled: true },
      { key: 'objectives', title: 'Objetivos', required: true, filled: false },
      { key: 'metrics', title: 'Métricas', required: false, filled: false },
    ]);
  });
});
