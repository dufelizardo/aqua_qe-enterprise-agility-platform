import { OrgLevel } from '../admin/organization.service';
import { contextViews, landingFor, plural } from './context-nav';

const LEVELS: OrgLevel[] = [
  { key: 'ENTERPRISE', name: 'Empresa', position: 1, flightLevel: null },
  { key: 'BUSINESS_UNIT', name: 'Unidade de Negócio', position: 2, flightLevel: null },
  { key: 'PORTFOLIO', name: 'Portfólio', position: 3, flightLevel: 'FL3' },
  { key: 'CADEIA', name: 'Cadeia', position: 4, flightLevel: 'FL2' },
  { key: 'SQUAD', name: 'Squad', position: 5, flightLevel: 'FL1' },
];
const views = (key: string) =>
  contextViews(LEVELS.find((l) => l.key === key)).map(
    (v) => `${v.label}|${v.path}${v.queryParams ? '?tab=' + v.queryParams['tab'] : ''}`,
  );

describe('contextViews (bug #66: as abas da página de cada unidade, como no desenho)', () => {
  it('Empresa, Unidade de Negócio, Portfólio e Cadeia: Wiki e Regimento, independentes', () => {
    for (const key of ['ENTERPRISE', 'BUSINESS_UNIT', 'PORTFOLIO', 'CADEIA']) {
      expect(views(key)).toEqual(['Wiki|/wiki', 'Regimento|/regimento']);
    }
  });

  it('Squad: Wiki, Regimento, Quadro, Backlog e Sprints', () => {
    expect(views('SQUAD')).toEqual([
      'Wiki|/wiki',
      'Regimento|/regimento',
      'Quadro|/squad',
      'Backlog|/squad?tab=backlog',
      'Sprints|/squad?tab=sprints',
    ]);
  });

  it('decide pelo Flight Level, não pelo nome: um nível "Team" ligado a FL1 tem quadro', () => {
    const team: OrgLevel = { key: 'T', name: 'Team', position: 3, flightLevel: 'FL1' };
    expect(contextViews(team).map((v) => v.label)).toEqual(['Wiki', 'Regimento', 'Quadro', 'Backlog', 'Sprints']);
  });
});

describe('plural', () => {
  it('regras simples do português', () => {
    expect(['Iniciativa', 'Feature', 'Ação', 'Setor', 'Item', 'Squads', 'Stream'].map(plural)).toEqual([
      'Iniciativas',
      'Features',
      'Ações',
      'Setores',
      'Itens',
      'Squads',
      'Streams',
    ]);
  });
});

describe('landingFor', () => {
  const squad = contextViews(LEVELS[4]);
  const cadeia = contextViews(LEVELS[3]);

  it('continua na mesma aba quando ela existe na nova unidade', () => {
    expect(landingFor(cadeia, '/wiki')).toBeNull();
    expect(landingFor(squad, '/squad')).toBeNull();
  });

  it('abre a primeira aba (Wiki) quando a aba atual não existe na nova unidade', () => {
    expect(landingFor(cadeia, '/squad')?.path).toBe('/wiki');
  });

  it('clicar numa unidade estando na Home abre a página dela', () => {
    expect(landingFor(cadeia, '/home')?.path).toBe('/wiki');
  });
});
