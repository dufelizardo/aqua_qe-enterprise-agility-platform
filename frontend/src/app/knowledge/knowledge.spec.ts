import { PageSummary, emptyBlock, pageTree, parseInline } from './knowledge.service';

describe('parseInline (Markdown leve, sem HTML)', () => {
  it('separa negrito, itálico, código e link', () => {
    expect(parseInline('Use **sempre** o *fluxo* `main` e veja [o guia](https://exemplo.com/guia).')).toEqual([
      { kind: 'text', text: 'Use ' },
      { kind: 'strong', text: 'sempre' },
      { kind: 'text', text: ' o ' },
      { kind: 'em', text: 'fluxo' },
      { kind: 'text', text: ' ' },
      { kind: 'code', text: 'main' },
      { kind: 'text', text: ' e veja ' },
      { kind: 'link', text: 'o guia', href: 'https://exemplo.com/guia' },
      { kind: 'text', text: '.' },
    ]);
  });

  it('não transforma links perigosos e mantém marcação HTML como texto', () => {
    expect(parseInline('[clique](javascript:alert(1))')).toEqual([{ kind: 'text', text: '[clique](javascript:alert(1)' }, { kind: 'text', text: ')' }]);
    expect(parseInline('<img src=x onerror=alert(1)>')).toEqual([{ kind: 'text', text: '<img src=x onerror=alert(1)>' }]);
  });

  it('aceita caminho interno e recusa caminho de outro host', () => {
    expect(parseInline('[wiki](/wiki?page=1)')[0]).toEqual({ kind: 'link', text: 'wiki', href: '/wiki?page=1' });
    expect(parseInline('[x](//evil.example)')[0]?.kind).toBe('text');
  });
});

describe('pageTree', () => {
  const page = (id: string, parentId: string | null): PageSummary => ({
    id,
    parentId,
    title: id,
    type: 'PAGE',
    governed: false,
    status: 'PUBLISHED',
    publishedVersion: 1,
    archived: false,
    updatedAt: '2026-11-20T00:00:00Z',
  });

  it('ordena pai antes dos filhos, com a profundidade', () => {
    const tree = pageTree([page('a', null), page('b', 'a'), page('c', 'b'), page('d', null)]);
    expect(tree.map((n) => `${n.depth}:${n.id}`)).toEqual(['0:a', '1:b', '2:c', '0:d']);
  });

  it('página cujo pai não aparece sobe para a raiz', () => {
    const tree = pageTree([page('filha', 'rascunho-oculto')]);
    expect(tree.map((n) => `${n.depth}:${n.id}`)).toEqual(['0:filha']);
  });
});

describe('emptyBlock', () => {
  it('cria cada tipo com os campos do contrato', () => {
    expect(emptyBlock('list')).toEqual({ type: 'list', ordered: false, items: [''] });
    expect(emptyBlock('heading')).toEqual({ type: 'heading', text: '', level: 2 });
    expect(emptyBlock('paragraph')).toEqual({ type: 'paragraph', text: '' });
  });
});
