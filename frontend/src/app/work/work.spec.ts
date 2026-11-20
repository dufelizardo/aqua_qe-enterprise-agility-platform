import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { LevelsService } from '../core/levels.service';
import { WorkspaceService } from '../core/workspace.service';
import { BoardColumn, ColumnConfig, TransitionOption, configProblems, executable, transitionInto } from './work.service';

const status = (key: string) => ({ key, name: key, phase: 'UPSTREAM' as const, category: 'TODO' as const });
const option = (id: string, to: string): TransitionOption => ({ id, to: status(to), canExecute: true, failedRules: [] });
const column = (keys: string[]): BoardColumn => ({
  id: keys.join(),
  name: keys.join(),
  phase: 'UPSTREAM',
  statusKeys: keys,
  wipLimit: null,
  wipPolicy: 'WARN',
  wipCount: null,
  wipState: 'NONE',
  cards: [],
});

describe('transitionInto', () => {
  it('escolhe a transição que leva a um status da coluna', () => {
    const options = [option('t1', 'BACKLOG'), option('t2', 'CANCELLED')];
    expect(transitionInto(options, column(['BACKLOG']))?.id).toBe('t1');
    expect(transitionInto(options, column(['DONE']))).toBeUndefined();
  });
});

describe('WorkspaceService', () => {
  let workspace: WorkspaceService;
  let http: HttpTestingController;
  const lvl = (key: string, position: number) => ({ key, name: key, position, flightLevel: null });
  const unit = (id: string, parentId: string | null, active = true, levelKey = 'PORTFOLIO', position = 3) => ({
    id,
    name: id,
    level: lvl(levelKey, position),
    parentId,
    active,
    version: 0,
  });
  const units = [unit('root', null, true, 'ENTERPRISE', 1), unit('a', 'root'), unit('b', 'root'), unit('off', 'root', false)];

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    workspace = TestBed.inject(WorkspaceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const flush = async (loading: Promise<void>, roleUnits: unknown[]) => {
    http.expectOne('/api/v1/me/workspaces').flush({ items: roleUnits });
    http.expectOne((r) => r.url === '/api/v1/org-units').flush({ items: units, page: 0, totalPages: 1 });
    await loading;
  };

  it('com uma única unidade com papel, escolhe essa unidade e oferece a árvore abaixo dela, sem as desativadas', async () => {
    await flush(workspace.load(), [{ id: 'root', name: 'Empresa', level: 'ENTERPRISE', roles: ['PRODUCT_MANAGER'] }]);
    expect(workspace.current()?.id).toBe('root');
    expect(workspace.workspaces().map((w) => `${w.depth}:${w.id}`)).toEqual(['0:root', '1:a', '1:b']);
    expect(workspace.selectable()).toBe(true);
    expect(workspace.hasRole('PRODUCT_MANAGER')).toBe(true);
    expect(workspace.hasRole('PLATFORM_ADMIN')).toBe(false);
  });

  it('com papéis em várias unidades, espera a escolha, herda os papéis de cima e lembra a escolha', async () => {
    const roleUnits = [
      { id: 'a', name: 'a', level: 'PORTFOLIO', roles: ['PRODUCT_MANAGER'] },
      { id: 'b', name: 'b', level: 'PORTFOLIO', roles: ['VIEWER'] },
    ];
    await flush(workspace.load(), roleUnits);
    expect(workspace.current()).toBeNull();
    expect(workspace.workspaces().map((w) => w.id)).toEqual(['a', 'b']);

    workspace.choose('b');
    await flush(workspace.load(), roleUnits);
    expect(workspace.current()?.roles).toEqual(['VIEWER']);
  });
});

describe('LevelsService', () => {
  it('usa os nomes de nível da configuração, mesmo diferentes da instalação padrão (Proposta 06)', async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const levels = TestBed.inject(LevelsService);
    const http = TestBed.inject(HttpTestingController);
    expect(levels.nameOf('FL3')).toBe('FL3');
    const loading = levels.load();
    http.expectOne('/api/v1/org-levels').flush({
      items: [
        { key: 'EMPRESA', name: 'Empresa', position: 1, flightLevel: null },
        { key: 'PRODUTO', name: 'Produto', position: 2, flightLevel: 'FL3' },
        { key: 'VALUE_STREAM', name: 'Value Stream', position: 3, flightLevel: 'FL2' },
        { key: 'TEAM', name: 'Team', position: 4, flightLevel: 'FL1' },
      ],
    });
    await loading;
    expect([levels.nameOf('FL3'), levels.nameOf('FL2'), levels.nameOf('FL1')]).toEqual(['Produto', 'Value Stream', 'Team']);
    expect(levels.nameOfKey('VALUE_STREAM')).toBe('Value Stream');
    http.verify();
  });
});

describe('executable', () => {
  it('descarta transições sem permissão ou com regra pendente (gate ou WIP)', () => {
    const blocked = { ...option('t3', 'FEASIBILITY'), failedRules: [{ ruleId: 'wip:1', type: 'WIP_LIMIT', message: 'cheia' }] };
    const forbidden = { ...option('t4', 'DONE'), canExecute: false };
    expect(executable([option('t1', 'BACKLOG'), blocked, forbidden]).map((o) => o.id)).toEqual(['t1']);
  });
});

describe('configProblems', () => {
  const col = (name: string, phase: 'UPSTREAM' | 'DOWNSTREAM', keys: string[], wipLimit: number | null = null): ColumnConfig => ({
    name,
    phase,
    statusKeys: keys,
    wipLimit,
    wipPolicy: 'WARN',
  });

  it('aceita uma configuração válida', () => {
    expect(configProblems([col('A', 'UPSTREAM', ['IDEA']), col('B', 'DOWNSTREAM', ['DONE'], 3)])).toEqual([]);
  });

  it('aponta status repetido, Upstream depois de Downstream, coluna vazia e limite inválido', () => {
    const problems = configProblems([
      col('A', 'DOWNSTREAM', ['IDEA']),
      col('B', 'UPSTREAM', ['IDEA']),
      col('C', 'DOWNSTREAM', []),
      col('D', 'DOWNSTREAM', ['DONE'], 0),
    ]);
    expect(problems).toHaveLength(4);
  });
});
