import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { OrgUnit, OrganizationService, buildTree } from '../admin/organization.service';

/** Unidade em que o usuário tem papel (GET /me/workspaces, SSD 3.1). */
export interface RoleUnit {
  id: string;
  name: string;
  level: string;
  roles: string[];
}

/**
 * Unidade que pode ser o workspace: uma unidade com papel ou qualquer unidade ativa abaixo dela (SSD 2.1, regra 1),
 * com a profundidade na árvore e os papéis que valem nela (os das unidades acima, com papel).
 */
export interface Workspace {
  id: string;
  name: string;
  level: string;
  depth: number;
  roles: string[];
}

/**
 * Nó da árvore organizacional do menu (Proposta 06, Revisão 2; PROPOSAL-07): as unidades que o usuário pode escolher e,
 * para ele se localizar, os ancestrais delas, que não são escolhíveis nem dão acesso aos dados (ORG-D03).
 */
export interface NavNode {
  id: string;
  name: string;
  level: string;
  flightLevel: string | null;
  parentId: string | null;
  depth: number;
  selectable: boolean;
  hasChildren: boolean;
}

const STORAGE_KEY = 'aquaqe.workspace';

/**
 * Workspace ativo (UX/UI 7.1; Proposta 06): a unidade em que o usuário está trabalhando, escolhida na árvore
 * organizacional. A escolha é lembrada no navegador e enviada em X-Workspace-Id nas chamadas à API. Sem escolha
 * lembrada e com uma única unidade com papel, ela é escolhida sozinha.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);
  private readonly organization = inject(OrganizationService);

  readonly roleUnits = signal<RoleUnit[]>([]);
  private readonly units = signal<OrgUnit[]>([]);
  readonly loaded = signal(false);
  private readonly currentId = signal<string | null>(null);

  /** Árvore das unidades que o usuário pode escolher, sem as desativadas (Proposta 06, NAV-019). */
  readonly workspaces = computed<Workspace[]>(() => selectableTree(this.units(), this.roleUnits()));
  readonly current = computed(() => this.workspaces().find((w) => w.id === this.currentId()) ?? null);
  /** O seletor do cabeçalho só aparece com mais de uma opção (UX/UI 4). */
  readonly selectable = computed(() => this.workspaces().length > 1);
  /** Árvore do menu: unidades escolhíveis e os ancestrais delas (só para localização). */
  readonly navigation = computed<NavNode[]>(() => navigationTree(this.units(), this.workspaces()));

  async load(): Promise<void> {
    const [response, units] = await Promise.all([
      firstValueFrom(this.http.get<{ items: RoleUnit[] }>('/api/v1/me/workspaces')),
      this.organization.allUnits(),
    ]);
    this.roleUnits.set(response.items);
    this.units.set(units);
    const options = this.workspaces();
    const remembered = readStorage();
    const chosen =
      options.find((w) => w.id === remembered) ??
      (response.items.length === 1 ? options.find((w) => w.id === response.items[0]!.id) : undefined);
    this.currentId.set(chosen?.id ?? null);
    this.loaded.set(true);
  }

  choose(id: string): void {
    this.currentId.set(id);
    writeStorage(id);
  }

  /** Id do workspace ativo, para o cabeçalho X-Workspace-Id. */
  currentWorkspaceId(): string | null {
    return this.current()?.id ?? null;
  }

  /** O usuário tem algum dos papéis no workspace ativo? Só decide o que mostrar; quem decide o acesso é a API. */
  hasRole(...roles: string[]): boolean {
    return this.current()?.roles.some((role) => roles.includes(role)) ?? false;
  }
}

/**
 * Unidades com papel e as ativas abaixo delas, em ordem de árvore; cada uma com os papéis que valem nela (regra 1 do
 * SSD 2.1: o papel vale na unidade e abaixo). A profundidade é relativa à unidade com papel mais alta.
 */
export function selectableTree(units: OrgUnit[], roleUnits: RoleUnit[]): Workspace[] {
  const rolesAt = new Map(roleUnits.map((r) => [r.id, r.roles]));
  const parentOf = new Map(units.map((u) => [u.id, u.parentId]));
  const inheritedRoles = (id: string): string[] => {
    const roles = new Set<string>();
    for (let current: string | null | undefined = id; current; current = parentOf.get(current)) {
      rolesAt.get(current)?.forEach((role) => roles.add(role));
    }
    return [...roles];
  };
  const result: Workspace[] = [];
  let baseDepth: number | null = null;
  for (const row of buildTree(units)) {
    const roles = inheritedRoles(row.unit.id);
    if (!row.unit.active || roles.length === 0) {
      continue;
    }
    baseDepth ??= row.depth;
    result.push({
      id: row.unit.id,
      name: row.unit.name,
      level: row.unit.level.key,
      depth: Math.max(0, row.depth - baseDepth),
      roles,
    });
  }
  // Papéis sem a árvore carregada (ex.: falha ao listar unidades): ao menos as unidades com papel.
  return result.length > 0 || units.length > 0
    ? result
    : roleUnits.map((r) => ({ id: r.id, name: r.name, level: r.level, depth: 0, roles: r.roles }));
}

/**
 * Árvore do menu (PROPOSAL-07): as unidades escolhíveis e os ancestrais delas, em ordem de árvore, sem as desativadas e
 * sem duplicar unidades por vínculo "atende a" (só a hierarquia real entra aqui).
 */
export function navigationTree(units: OrgUnit[], workspaces: Workspace[]): NavNode[] {
  const selectable = new Set(workspaces.map((w) => w.id));
  const parentOf = new Map(units.map((u) => [u.id, u.parentId]));
  const included = new Set(selectable);
  for (const id of selectable) {
    for (let current = parentOf.get(id); current; current = parentOf.get(current)) {
      included.add(current);
    }
  }
  const rows = buildTree(units).filter((r) => r.unit.active && included.has(r.unit.id));
  const withChildren = new Set(rows.map((r) => r.unit.parentId).filter((id): id is string => !!id));
  return rows.map((r) => ({
    id: r.unit.id,
    name: r.unit.name,
    level: r.unit.level.key,
    flightLevel: r.unit.level.flightLevel,
    parentId: r.unit.parentId,
    depth: r.depth,
    selectable: selectable.has(r.unit.id),
    hasChildren: withChildren.has(r.unit.id),
  }));
}

/** Telas que listam itens exigem workspace; sem ele, o usuário vai à seleção (UX/UI 7.1). */
export const workspaceGuard: CanActivateFn = async (_route, state) => {
  const workspace = inject(WorkspaceService);
  const router = inject(Router);
  if (!workspace.loaded()) {
    await workspace.load();
  }
  return workspace.current() ? true : router.createUrlTree(['/workspace'], { queryParams: { voltar: state.url } });
};

/** Envia o workspace ativo em toda chamada à API (SSD 1.2). */
export const workspaceInterceptor: HttpInterceptorFn = (request, next) => {
  const id = inject(WorkspaceService).currentWorkspaceId();
  if (id && request.url.startsWith('/api/') && !request.headers.has('X-Workspace-Id')) {
    return next(request.clone({ setHeaders: { 'X-Workspace-Id': id } }));
  }
  return next(request);
};

function readStorage(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStorage(id: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, id);
  } catch {
    // Sem armazenamento (janela privada): a escolha vale só para esta sessão.
  }
}
