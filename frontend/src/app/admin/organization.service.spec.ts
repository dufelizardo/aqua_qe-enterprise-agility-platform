import { OrgLevel, OrgUnit, OrgUnitDetail, buildTree, linkCandidates } from './organization.service';

const level = (key: string, position: number): OrgLevel => ({ key, name: key, position, flightLevel: null });
const ENTERPRISE = level('ENTERPRISE', 1);
const PORTFOLIO = level('PORTFOLIO', 3);
const CADEIA = level('CADEIA', 4);

const unit = (id: string, name: string, lvl: OrgLevel, parentId: string | null, active = true): OrgUnit => ({
  id,
  name,
  level: lvl,
  parentId,
  active,
  version: 0,
});

// Empresa › Portfólio A › Cadeia Pagamentos; Empresa › Portfólio B; Portfólio C desativado.
const units: OrgUnit[] = [
  unit('cadeia', 'Cadeia Pagamentos', CADEIA, 'a'),
  unit('b', 'Portfólio B', PORTFOLIO, 'root'),
  unit('root', 'Empresa', ENTERPRISE, null),
  unit('a', 'Portfólio A', PORTFOLIO, 'root'),
  unit('c', 'Portfólio C', PORTFOLIO, 'root', false),
];

const detailOf = (u: OrgUnit, serves: string[] = []): OrgUnitDetail => ({
  ...u,
  path: [],
  links: { serves: serves.map((id) => ({ id, name: id, level: 'PORTFOLIO' })), servedBy: [] },
});

describe('buildTree', () => {
  it('põe a raiz primeiro e cada filho logo abaixo do pai, em ordem de nome', () => {
    const rows = buildTree(units);
    expect(rows.map((r) => `${r.depth}:${r.unit.name}`)).toEqual([
      '0:Empresa',
      '1:Portfólio A',
      '2:Cadeia Pagamentos',
      '1:Portfólio B',
      '1:Portfólio C',
    ]);
  });
});

describe('linkCandidates', () => {
  it('oferece só unidades ativas de outro ramo (cenário C2)', () => {
    const cadeia = detailOf(units[0]!);
    expect(linkCandidates(cadeia, units).map((u) => u.id)).toEqual(['b']);
  });

  it('não oferece unidade já vinculada', () => {
    const cadeia = detailOf(units[0]!, ['b']);
    expect(linkCandidates(cadeia, units)).toEqual([]);
  });

  it('não oferece descendentes da própria unidade', () => {
    const portfolioA = detailOf(units[3]!);
    expect(linkCandidates(portfolioA, units).map((u) => u.id)).toEqual(['b']);
  });
});
