import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

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

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    workspace = TestBed.inject(WorkspaceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('com uma única unidade, escolhe sozinho e esconde o seletor', async () => {
    const loading = workspace.load();
    http.expectOne('/api/v1/me/workspaces').flush({
      items: [{ id: 'root', name: 'Empresa', level: 'ENTERPRISE', roles: ['PRODUCT_MANAGER'] }],
    });
    await loading;
    expect(workspace.current()?.id).toBe('root');
    expect(workspace.selectable()).toBe(false);
    expect(workspace.hasRole('PRODUCT_MANAGER')).toBe(true);
    expect(workspace.hasRole('PLATFORM_ADMIN')).toBe(false);
  });

  it('com várias unidades, espera a escolha e a lembra', async () => {
    const items = [
      { id: 'a', name: 'Portfólio A', level: 'PORTFOLIO', roles: ['PRODUCT_MANAGER'] },
      { id: 'b', name: 'Portfólio B', level: 'PORTFOLIO', roles: ['VIEWER'] },
    ];
    let loading = workspace.load();
    http.expectOne('/api/v1/me/workspaces').flush({ items });
    await loading;
    expect(workspace.current()).toBeNull();
    expect(workspace.selectable()).toBe(true);

    workspace.choose('b');
    loading = workspace.load();
    http.expectOne('/api/v1/me/workspaces').flush({ items });
    await loading;
    expect(workspace.current()?.name).toBe('Portfólio B');
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
