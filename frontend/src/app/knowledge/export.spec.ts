import { documentMarkdownUrl } from './artifact-documents.service';
import { pageMarkdownUrl } from './knowledge.service';

describe('Exportação para Markdown (#16)', () => {
  it('o documento do artefato e a página do Wiki têm o endereço do Markdown', () => {
    expect(documentMarkdownUrl('INIT-12', 'PRD')).toBe('/api/v1/artifacts/INIT-12/documents/PRD/export/markdown');
    expect(pageMarkdownUrl('p 1')).toBe('/api/v1/pages/p%201/export/markdown');
  });
});
