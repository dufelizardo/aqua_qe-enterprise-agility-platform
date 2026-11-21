import { OrgLevel } from '../admin/organization.service';
import { contextViews, landingFor, plural } from './context-nav';

const LEVELS: OrgLevel[] = [
  { key: 'ENTERPRISE', name: 'Empresa', position: 1, flightLevel: null },
  { key: 'BUSINESS_UNIT', name: 'Unidade de Negócio', position: 2, flightLevel: null },
  { key: 'PORTFOLIO', name: 'Portfólio', position: 3, flightLevel: 'FL3' },
  { key: 'CADEIA', name: 'Cadeia', position: 4, flightLevel: 'FL2' },
  { key: 'SQUAD', name: 'Squad', position: 5, flightLevel: 'FL1' },
];
const TYPES = new Map([
  ['INITIATIVE', 'Iniciativa'],
  ['FEATURE', 'Feature'],
]);
const views = (key: string) =>
  contextViews(
    LEVELS.find((l) => l.key === key),
    LEVELS,
    (k) => TYPES.get(k) ?? null,
  ).map((v) => `${v.label}|${v.path}${v.queryParams ? '?tab=' + v.queryParams['tab'] : ''}`);

describe('contextViews (bug #66: o menu acompanha a unidade)', () => {
  it('Portfólio: Iniciativas e o consolidado dos níveis abaixo, nunca os de cima', () => {
    expect(views('PORTFOLIO')).toEqual([
      'Iniciativas|/portfolio',
      'Cadeias abaixo|/tactical',
      'Squads abaixo|/squad',
      'Wiki|/wiki',
    ]);
  });

  it('Cadeia: Features e as Squads abaixo', () => {
    expect(views('CADEIA')).toEqual(['Features|/tactical', 'Squads abaixo|/squad', 'Wiki|/wiki']);
  });

  it('Squad: Quadro, Backlog e Sprints, sem visões de cima', () => {
    expect(views('SQUAD')).toEqual([
      'Quadro|/squad',
      'Backlog|/squad?tab=backlog',
      'Sprints|/squad?tab=sprints',
      'Wiki|/wiki',
    ]);
  });

  it('Empresa e Unidade de Negócio: o consolidado de todos os Flight Levels', () => {
    const expected = ['Portfólios abaixo|/portfolio', 'Cadeias abaixo|/tactical', 'Squads abaixo|/squad', 'Wiki|/wiki'];
    expect(views('ENTERPRISE')).toEqual(expected);
    expect(views('BUSINESS_UNIT')).toEqual(expected);
  });

  it('usa os nomes da configuração: uma instalação com "Produto", "Value Stream" e "Team" vê esses nomes', () => {
    const custom: OrgLevel[] = [
      { key: 'P', name: 'Produto', position: 1, flightLevel: 'FL3' },
      { key: 'V', name: 'Value Stream', position: 2, flightLevel: 'FL2' },
      { key: 'T', name: 'Team', position: 3, flightLevel: 'FL1' },
    ];
    const labels = contextViews(custom[0], custom, () => 'Épico').map((v) => v.label);
    expect(labels).toEqual(['Épicos', 'Value Streams abaixo', 'Teams abaixo', 'Wiki']);
  });

  it('sem os tipos carregados, usa o nome do nível', () => {
    expect(contextViews(LEVELS[2], LEVELS, () => null)[0]?.label).toBe('Portfólios');
  });
});

describe('plural', () => {
  it('regras simples do português', () => {
    expect(['Iniciativa', 'Feature', 'Ação', 'Setor', 'Item', 'Squads'].map(plural)).toEqual([
      'Iniciativas',
      'Features',
      'Ações',
      'Setores',
      'Itens',
      'Squads',
    ]);
  });
});

describe('landingFor', () => {
  const squadViews = contextViews(LEVELS[4], LEVELS, () => null);
  const enterpriseViews = contextViews(LEVELS[0], LEVELS, () => null);

  it('unidade com Flight Level abre a primeira visão dela', () => {
    expect(landingFor(squadViews, '/portfolio', 'FL1')?.label).toBe('Quadro');
  });

  it('no Wiki, continua no Wiki', () => {
    expect(landingFor(squadViews, '/wiki', 'FL1')).toBeNull();
  });

  it('sem Flight Level, mantém a tela atual (o consolidado de todos os níveis está no contexto)', () => {
    expect(landingFor(enterpriseViews, '/tactical', null)).toBeNull();
    expect(landingFor(enterpriseViews, '/home', null)).toBeNull();
  });
});
