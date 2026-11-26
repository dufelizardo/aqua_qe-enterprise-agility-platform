import { docHeadings } from './doc-outline';
import { EMOJIS, HIGHLIGHTS, TEXT_COLORS, isHexColor } from './editor-palettes';
import { RichNode } from './knowledge.service';

const heading = (level: number, text: string): RichNode => ({
  type: 'heading',
  attrs: { level },
  content: [{ type: 'text', text }],
});

describe('editor: sumário, cores e emojis (#86)', () => {
  it('o sumário lista os títulos e subtítulos na ordem, em todas as seções', () => {
    const first: RichNode = {
      type: 'doc',
      content: [heading(2, 'Objetivo'), { type: 'paragraph', content: [{ type: 'text', text: 'texto' }] }, heading(3, 'Escopo')],
    };
    const second: RichNode = {
      type: 'doc',
      content: [{ type: 'blockquote', content: [heading(2, 'Dentro da citação')] }, heading(2, '   ')],
    };
    expect(docHeadings([first, second])).toEqual([
      { level: 2, text: 'Objetivo' },
      { level: 3, text: 'Escopo' },
      { level: 2, text: 'Dentro da citação' },
    ]);
    expect(docHeadings([])).toEqual([]);
  });

  it('as cores das paletas estão no formato que o servidor aceita', () => {
    for (const swatch of [...TEXT_COLORS, ...HIGHLIGHTS]) {
      expect(isHexColor(swatch.color)).toBe(true);
    }
    expect(isHexColor('red; background: url(x)')).toBe(false);
  });

  it('os emojis não têm caracteres do teclado (o servidor recusa ícone com eles)', () => {
    for (const emoji of EMOJIS) {
      expect([...emoji].every((c) => c.codePointAt(0)! > 0x7f)).toBe(true);
      expect(emoji.length).toBeLessThanOrEqual(16);
    }
    expect(new Set(EMOJIS).size).toBe(EMOJIS.length);
  });
});
