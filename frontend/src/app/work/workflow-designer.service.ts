import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Workflow Designer (#49; UI-26; SSD 3.3): workflow próprio por unidade, com versões e migração explícita. */

export type Phase = 'UPSTREAM' | 'DOWNSTREAM';
export type Category = 'TODO' | 'IN_PROGRESS' | 'DONE';

export interface WorkflowStatus {
  id: string;
  key: string;
  name: string;
  phase: Phase | null;
  category: Category;
  /** Nula para status fora da sequência (Cancelado). */
  position: number | null;
}

export interface WorkflowVersion {
  id: string;
  familyId: string;
  name: string;
  artifactTypes: string[];
  versionNumber: number;
  active: boolean;
  orgUnit: { id: string; name: string } | null;
  publishedAt: string;
  statuses: WorkflowStatus[];
  transitions: { id: string; from: string; to: string }[];
}

export interface UnitWorkflow {
  workflow: WorkflowVersion;
  /** Vem de uma unidade acima. */
  inherited: boolean;
  source: { id: string; name: string } | null;
}

export interface MigrationPreview {
  workflowId: string;
  versionNumber: number;
  total: number;
  pending: { statusKey: string; statusName: string; items: number; suggestedKey: string | null }[];
  targets: { key: string; name: string }[];
}

/** Status no editor: {@code isNew} enquanto não foi publicado (só então a chave pode mudar). */
export interface DraftStatus {
  key: string;
  name: string;
  phase: Phase | null;
  category: Category;
  inSequence: boolean;
  isNew: boolean;
}

export interface Draft {
  name: string;
  statuses: DraftStatus[];
  /** Transições como "DE>PARA". */
  transitions: Set<string>;
}

export const PHASE_LABELS: Record<Phase, string> = { UPSTREAM: 'Upstream', DOWNSTREAM: 'Downstream' };
export const CATEGORY_LABELS: Record<Category, string> = {
  TODO: 'A fazer',
  IN_PROGRESS: 'Em andamento',
  DONE: 'Concluído',
};

/** Rascunho a partir de uma versão publicada: status em ordem (os da sequência primeiro) e transições. */
export function draftOf(workflow: WorkflowVersion): Draft {
  const statuses = [...workflow.statuses]
    .sort((a, b) => (a.position ?? Number.MAX_SAFE_INTEGER) - (b.position ?? Number.MAX_SAFE_INTEGER))
    .map((s) => ({
      key: s.key,
      name: s.name,
      phase: s.phase,
      category: s.category,
      inSequence: s.position !== null,
      isNew: false,
    }));
  return { name: workflow.name, statuses, transitions: new Set(workflow.transitions.map((t) => `${t.from}>${t.to}`)) };
}

/**
 * Chave de um status novo a partir do nome: sem acentos, maiúsculas, "_" no lugar do resto, até 40 caracteres e sem
 * repetir as existentes ("EM_TESTE", "EM_TESTE_2").
 */
export function keyFromName(name: string, existing: Iterable<string>): string {
  let base = name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  if (!base) {
    base = 'STATUS';
  }
  if (!/^[A-Z]/.test(base)) {
    base = `S_${base}`;
  }
  base = base.slice(0, 40);
  const taken = new Set(existing);
  if (!taken.has(base)) {
    return base;
  }
  for (let i = 2; ; i++) {
    const suffix = `_${i}`;
    const candidate = base.slice(0, 40 - suffix.length) + suffix;
    if (!taken.has(candidate)) {
      return candidate;
    }
  }
}

/** Remove o status do rascunho e as transições que o usam. */
export function removeStatus(draft: Draft, key: string): Draft {
  return {
    ...draft,
    statuses: draft.statuses.filter((s) => s.key !== key),
    transitions: new Set([...draft.transitions].filter((t) => !t.split('>').includes(key))),
  };
}

/** Corpo de {@code POST /workflows/{id}/versions}: a ordem da lista é a da sequência. */
export function versionBody(draft: Draft): {
  name: string;
  statuses: { key: string; name: string; phase: Phase | null; category: Category; inSequence: boolean }[];
  transitions: { from: string; to: string }[];
} {
  const keys = new Set(draft.statuses.map((s) => s.key));
  return {
    name: draft.name.trim(),
    statuses: draft.statuses.map(({ key, name, phase, category, inSequence }) => ({
      key,
      name: name.trim(),
      phase,
      category,
      inSequence,
    })),
    transitions: [...draft.transitions]
      .map((t) => t.split('>'))
      .filter(([from, to]) => keys.has(from) && keys.has(to))
      .map(([from, to]) => ({ from, to })),
  };
}

/** Status que a versão publicada tinha e o rascunho não tem mais: os itens neles vão precisar de destino na migração. */
export function removedKeys(published: WorkflowVersion, draft: Draft): string[] {
  const kept = new Set(draft.statuses.map((s) => s.key));
  return published.statuses.map((s) => s.key).filter((k) => !kept.has(k));
}

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class WorkflowDesignerService {
  private readonly http = inject(HttpClient);

  async ofUnit(unitId: string): Promise<UnitWorkflow[]> {
    return (await firstValueFrom(this.http.get<{ items: UnitWorkflow[] }>(`${API}/org-units/${unitId}/workflows`))).items;
  }

  customize(unitId: string, fromWorkflowId: string): Promise<WorkflowVersion> {
    return firstValueFrom(this.http.post<WorkflowVersion>(`${API}/org-units/${unitId}/workflows`, { from: fromWorkflowId }));
  }

  publish(workflowId: string, draft: Draft): Promise<WorkflowVersion> {
    return firstValueFrom(this.http.post<WorkflowVersion>(`${API}/workflows/${workflowId}/versions`, versionBody(draft)));
  }

  revert(workflowId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API}/workflows/${workflowId}`));
  }

  migrationPreview(workflowId: string): Promise<MigrationPreview> {
    return firstValueFrom(this.http.get<MigrationPreview>(`${API}/workflows/${workflowId}/migration`));
  }

  migrate(workflowId: string, mapping: Record<string, string>): Promise<{ migrated: number }> {
    return firstValueFrom(this.http.post<{ migrated: number }>(`${API}/workflows/${workflowId}/migration`, { mapping }));
  }
}
