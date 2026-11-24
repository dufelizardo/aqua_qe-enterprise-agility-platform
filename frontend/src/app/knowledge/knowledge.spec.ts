import {
  Block,
  PageSummary,
  blocksToDoc,
  docHasText,
  docToBlocks,
  dropRank,
  moveTargets,
  pageTree,
  parseInline,
  rankFor,
} from './knowledge.service';

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

describe('blocksToDoc (bug #67: conteúdo do editor anterior no editor contínuo, sem perda)', () => {
  it('converte parágrafo com Markdown leve em marcas', () => {
    const doc = blocksToDoc([{ type: 'paragraph', text: 'Use **sempre** o *fluxo* `main` e [o guia](https://x.dev).' }]);
    expect(doc.content?.[0]?.content).toEqual([
      { type: 'text', text: 'Use ' },
      { type: 'text', text: 'sempre', marks: [{ type: 'bold' }] },
      { type: 'text', text: ' o ' },
      { type: 'text', text: 'fluxo', marks: [{ type: 'italic' }] },
      { type: 'text', text: ' ' },
      { type: 'text', text: 'main', marks: [{ type: 'code' }] },
      { type: 'text', text: ' e ' },
      { type: 'text', text: 'o guia', marks: [{ type: 'link', attrs: { href: 'https://x.dev' } }] },
      { type: 'text', text: '.' },
    ]);
  });

  it('converte título, lista, tabela, código, destaque e citação', () => {
    const blocks: Block[] = [
      { type: 'heading', text: 'Objetivos', level: 2 },
      { type: 'list', ordered: true, items: ['Um', 'Dois', ''] },
      { type: 'table', rows: [['A ', ' B'], ['1', '2']] },
      { type: 'code', text: 'mvn verify' },
      { type: 'callout', text: 'Atenção', tone: 'warning' },
      { type: 'quote', text: 'Citação' },
    ];
    const types = blocksToDoc(blocks).content?.map((n) => n.type);
    expect(types).toEqual(['heading', 'orderedList', 'table', 'codeBlock', 'blockquote', 'blockquote']);
    const table = blocksToDoc([blocks[2]!]).content?.[0];
    expect(table?.content?.[0]?.content?.[0]?.type).toBe('tableHeader');
    expect(table?.content?.[0]?.content?.[0]?.content?.[0]?.content?.[0]?.text).toBe('A');
    expect(blocksToDoc([blocks[1]!]).content?.[0]?.content).toHaveLength(2);
  });

  it('o bloco richtext volta como está, e o documento vai e volta sem perda', () => {
    const doc = blocksToDoc([{ type: 'paragraph', text: 'Texto' }]);
    expect(blocksToDoc(docToBlocks(doc))).toEqual(doc);
  });

  it('seção vazia vira um parágrafo vazio e não conta como preenchida', () => {
    const doc = blocksToDoc([]);
    expect(doc).toEqual({ type: 'doc', content: [{ type: 'paragraph' }] });
    expect(docHasText(doc)).toBe(false);
    expect(docHasText(blocksToDoc([{ type: 'paragraph', text: 'x' }]))).toBe(true);
  });
});

describe('organizar a árvore (PROP-002)', () => {
  const p = (id: string, parentId: string | null): PageSummary => ({
    id,
    parentId,
    title: id,
    type: 'PAGE',
    governed: false,
    status: 'PUBLISHED',
    publishedVersion: 1,
    archived: false,
    updatedAt: '2026-11-24T00:00:00Z',
  });
  // Raiz: a, b, c; a tem a1 e a2; a1 tem a1x.
  const items = [p('a', null), p('a1', 'a'), p('a1x', 'a1'), p('a2', 'a'), p('b', null), p('c', null)];

  it('subir e descer usam a irmã vizinha; nas pontas não se aplicam', () => {
    expect(rankFor(items, 'b', 'up')).toEqual({ beforeId: 'a' });
    expect(rankFor(items, 'b', 'down')).toEqual({ afterId: 'c' });
    expect(rankFor(items, 'a', 'up')).toBeNull();
    expect(rankFor(items, 'c', 'down')).toBeNull();
    expect(rankFor(items, 'a2', 'up')).toEqual({ beforeId: 'a1' });
  });

  it('promover vai para o nível do pai, logo depois dele; na raiz não se aplica', () => {
    expect(rankFor(items, 'a2', 'promote')).toEqual({ parentId: null, afterId: 'a' });
    expect(rankFor(items, 'a1x', 'promote')).toEqual({ parentId: 'a', afterId: 'a1' });
    expect(rankFor(items, 'b', 'promote')).toBeNull();
  });

  it('rebaixar vira subpágina da irmã de cima; a primeira não se aplica', () => {
    expect(rankFor(items, 'b', 'demote')).toEqual({ parentId: 'a' });
    expect(rankFor(items, 'a', 'demote')).toBeNull();
  });

  it('Mover para… não oferece a própria página nem as descendentes', () => {
    expect(moveTargets(items, 'a').map((n) => n.id)).toEqual(['b', 'c']);
    expect(moveTargets(items, 'a1').map((n) => n.id)).toEqual(['a', 'a2', 'b', 'c']);
  });

  it('arrastar põe a página onde foi solta, no nível da linha vizinha', () => {
    const tree = pageTree(items); // a, a1, a1x, a2, b, c
    expect(dropRank(tree, 4, 5)).toEqual({ id: 'b', request: { afterId: 'c' } });
    expect(dropRank(tree, 5, 0)).toEqual({ id: 'c', request: { beforeId: 'a' } });
    // b solta sobre a1 (subindo): vira irmã de a1, antes dela.
    expect(dropRank(tree, 4, 1)).toEqual({ id: 'b', request: { parentId: 'a', beforeId: 'a1' } });
    // a2 solta abaixo de b (descendo): vai para a raiz, depois de b.
    expect(dropRank(tree, 3, 4)).toEqual({ id: 'a2', request: { parentId: null, afterId: 'b' } });
    // c solta sobre a1x (subindo): vira subpágina de a1, antes de a1x.
    expect(dropRank(tree, 5, 2)).toEqual({ id: 'c', request: { parentId: 'a1', beforeId: 'a1x' } });
    // a2 solta logo abaixo de b, que agora tem subpágina: vira a primeira subpágina de b.
    const withChild = pageTree([...items, p('b1', 'b')]); // a, a1, a1x, a2, b, b1, c
    expect(dropRank(withChild, 3, 4)).toEqual({ id: 'a2', request: { parentId: 'b', beforeId: 'b1' } });
    expect(dropRank(tree, 3, 3)).toBeNull();
  });

  it('arrastar não põe a página dentro dela mesma', () => {
    const tree = pageTree(items);
    expect(dropRank(tree, 0, 2)).toBeNull();
    expect(dropRank(tree, 1, 2)).toBeNull();
  });
});
