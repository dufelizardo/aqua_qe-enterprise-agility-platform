import { TestBed } from '@angular/core/testing';

import { DraftStore, StoredDraft, draftBlocks, draftKey, draftMatch } from './draft-store';

const stored = (baseVersion: number): StoredDraft => ({
  baseVersion,
  title: 'Título',
  sections: [{ key: 'content', blocks: [{ type: 'paragraph', text: 'Texto do rascunho' }] }],
  savedAt: '2026-11-25T20:00:00.000Z',
});

describe('rascunho automático (#82)', () => {
  beforeEach(() => localStorage.clear());

  it('a chave separa páginas do Wiki e documentos do artefato', () => {
    expect(draftKey('page', 'p1')).toBe('aquaqe.draft.page.p1');
    expect(draftKey('doc', 'INIT-1.PRD')).toBe('aquaqe.draft.doc.INIT-1.PRD');
  });

  it('o rascunho serve para a mesma versão; de outra versão, só se a pessoa pedir', () => {
    expect(draftMatch(null, 3)).toBeNull();
    expect(draftMatch(stored(3), 3)).toBe('same');
    expect(draftMatch(stored(2), 3)).toBe('older');
  });

  it('repõe só as seções que ainda existem', () => {
    expect(draftBlocks(stored(1), 'content')).toEqual([{ type: 'paragraph', text: 'Texto do rascunho' }]);
    expect(draftBlocks(stored(1), 'outra')).toBeUndefined();
  });

  it('guarda, lê e apaga; ignora conteúdo inválido', () => {
    const drafts = TestBed.inject(DraftStore);
    const key = draftKey('page', 'p1');
    expect(drafts.write(key, stored(4))).toBe(true);
    expect(drafts.read(key)?.baseVersion).toBe(4);
    drafts.clear(key);
    expect(drafts.read(key)).toBeNull();
    localStorage.setItem(key, '{não é json');
    expect(drafts.read(key)).toBeNull();
    localStorage.setItem(key, '{"sections":[]}');
    expect(drafts.read(key)).toBeNull();
  });
});
