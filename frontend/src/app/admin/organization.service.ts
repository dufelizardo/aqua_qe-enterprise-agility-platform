import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Nível organizacional da instalação (SSD, seção 3.2). */
export interface OrgLevel {
  key: string;
  name: string;
  position: number;
  flightLevel: 'FL1' | 'FL2' | 'FL3' | null;
}

export interface OrgUnit {
  id: string;
  name: string;
  level: OrgLevel;
  parentId: string | null;
  active: boolean;
  version: number;
}

export interface OrgUnitRef {
  id: string;
  name: string;
  level: string;
}

export interface OrgUnitDetail extends OrgUnit {
  /** Da raiz até o pai. */
  path: OrgUnitRef[];
  links: { serves: OrgUnitRef[]; servedBy: OrgUnitRef[] };
}

/** O que ainda está ligado à unidade, para o aviso antes de desativá-la (#35). */
export interface DeactivationImpact {
  activeSubunits: number;
  openArtifacts: number;
  activeRoleAssignments: number;
  links: number;
}

/** As linhas do aviso, só com o que existe: "2 subunidades ativas", "1 artefato aberto"... */
export function impactLines(impact: DeactivationImpact): string[] {
  const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;
  const lines: string[] = [];
  if (impact.activeSubunits > 0) {
    lines.push(
      plural(impact.activeSubunits, 'subunidade ativa abaixo (continua ativa)', 'subunidades ativas abaixo (continuam ativas)'),
    );
  }
  if (impact.openArtifacts > 0) {
    lines.push(plural(impact.openArtifacts, 'artefato aberto (continua na unidade)', 'artefatos abertos (continuam na unidade)'));
  }
  if (impact.activeRoleAssignments > 0) {
    lines.push(plural(impact.activeRoleAssignments, 'papel vigente (não é revogado)', 'papéis vigentes (não são revogados)'));
  }
  if (impact.links > 0) {
    lines.push(plural(impact.links, 'vínculo "atende a" (é mantido)', 'vínculos "atende a" (são mantidos)'));
  }
  return lines;
}

/** Linha da árvore já achatada para exibição: a unidade e a profundidade. */
export interface TreeRow {
  unit: OrgUnit;
  depth: number;
}

interface Page<T> {
  items: T[];
  page: number;
  totalPages: number;
}

const API = '/api/v1';
const PAGE_SIZE = 100;

/** Endpoints do módulo organization (SSD, seção 3.2). */
@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly http = inject(HttpClient);

  async levels(): Promise<OrgLevel[]> {
    return (await firstValueFrom(this.http.get<{ items: OrgLevel[] }>(`${API}/org-levels`))).items;
  }

  /** Todas as unidades, página a página (o limite da API é 100 por página). */
  async allUnits(): Promise<OrgUnit[]> {
    const units: OrgUnit[] = [];
    for (let page = 0; ; page++) {
      const result = await firstValueFrom(
        this.http.get<Page<OrgUnit>>(`${API}/org-units`, { params: { page, size: PAGE_SIZE } }),
      );
      units.push(...result.items);
      if (page + 1 >= result.totalPages) {
        return units;
      }
    }
  }

  unit(id: string): Promise<OrgUnitDetail> {
    return firstValueFrom(this.http.get<OrgUnitDetail>(`${API}/org-units/${id}`));
  }

  create(name: string, level: string, parentId: string): Promise<OrgUnitDetail> {
    return firstValueFrom(this.http.post<OrgUnitDetail>(`${API}/org-units`, { name, level, parentId }));
  }

  update(id: string, changes: { name?: string; active?: boolean; version: number }): Promise<OrgUnitDetail> {
    return firstValueFrom(this.http.patch<OrgUnitDetail>(`${API}/org-units/${id}`, changes));
  }

  deactivationImpact(id: string): Promise<DeactivationImpact> {
    return firstValueFrom(this.http.get<DeactivationImpact>(`${API}/org-units/${id}/deactivation-impact`));
  }

  link(id: string, targetId: string): Promise<unknown> {
    return firstValueFrom(this.http.put(`${API}/org-units/${id}/links/${targetId}`, null));
  }

  unlink(id: string, targetId: string): Promise<unknown> {
    return firstValueFrom(this.http.delete(`${API}/org-units/${id}/links/${targetId}`));
  }
}

/** Monta a árvore (raiz primeiro, filhos ordenados por nível e nome) e a achata com a profundidade de cada unidade. */
export function buildTree(units: OrgUnit[]): TreeRow[] {
  const children = new Map<string | null, OrgUnit[]>();
  for (const unit of units) {
    const siblings = children.get(unit.parentId) ?? [];
    siblings.push(unit);
    children.set(unit.parentId, siblings);
  }
  const rows: TreeRow[] = [];
  const visit = (parentId: string | null, depth: number) => {
    const siblings = [...(children.get(parentId) ?? [])].sort(
      (a, b) => a.level.position - b.level.position || a.name.localeCompare(b.name, 'pt-BR'),
    );
    for (const unit of siblings) {
      rows.push({ unit, depth });
      visit(unit.id, depth + 1);
    }
  };
  visit(null, 0);
  return rows;
}

/**
 * Unidades que podem receber um vínculo "atende a" desta (SSD 3.2): ativas, ainda não vinculadas e fora da mesma linha
 * da árvore (nem ancestral, nem descendente, nem ela mesma).
 */
export function linkCandidates(unit: OrgUnitDetail, units: OrgUnit[]): OrgUnit[] {
  const parentOf = new Map(units.map((u) => [u.id, u.parentId]));
  const ancestorsOf = (id: string): Set<string> => {
    const ancestors = new Set<string>();
    for (let current = parentOf.get(id) ?? null; current; current = parentOf.get(current) ?? null) {
      ancestors.add(current);
    }
    return ancestors;
  };
  const mine = ancestorsOf(unit.id);
  const linked = new Set(unit.links.serves.map((ref) => ref.id));
  return units
    .filter((u) => u.active && u.id !== unit.id && !linked.has(u.id))
    .filter((u) => !mine.has(u.id) && !ancestorsOf(u.id).has(unit.id))
    .sort((a, b) => a.level.position - b.level.position || a.name.localeCompare(b.name, 'pt-BR'));
}
