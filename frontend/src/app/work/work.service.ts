import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Pessoa referida em um artefato (SSD 3.4). */
export interface PersonRef {
  id: string;
  name: string;
}

export interface StatusRef {
  key: string;
  name: string;
  phase: 'UPSTREAM' | 'DOWNSTREAM' | null;
  category: 'TODO' | 'IN_PROGRESS' | 'DONE';
}

/** Artefato (SSD 3.4). */
export interface Artifact {
  id: string;
  key: string;
  type: string;
  level: string;
  title: string;
  description: string | null;
  orgUnit: { id: string; name: string; level: string };
  status: StatusRef;
  owner: PersonRef | null;
  size: string | null;
  childCount: number;
  version: number;
  blocked: boolean;
  blockedReason: string | null;
}

/** Cartão do quadro (UX/UI 6.1; SSD 3.9). */
export interface Card {
  id: string;
  key: string;
  type: string;
  title: string;
  owner: PersonRef | null;
  size: string | null;
  version: number;
  blocked: boolean;
  blockedReason: string | null;
}

export type WipPolicy = 'WARN' | 'BLOCK';
export type WipState = 'NONE' | 'UNDER' | 'AT' | 'OVER';

export interface BoardColumn {
  id: string;
  name: string;
  phase: 'UPSTREAM' | 'DOWNSTREAM';
  statusKeys: string[];
  wipLimit: number | null;
  wipPolicy: WipPolicy;
  /** Conta todos os itens da coluna, não só os visíveis; nulo sem limite. */
  wipCount: number | null;
  wipState: WipState;
  cards: Card[];
}

/** Coluna na configuração do quadro (PATCH /boards/{id}). */
export interface ColumnConfig {
  id?: string;
  name: string;
  phase: 'UPSTREAM' | 'DOWNSTREAM';
  statusKeys: string[];
  wipLimit: number | null;
  wipPolicy: WipPolicy;
}

export interface Board {
  id: string;
  orgUnitId: string;
  level: string;
  name: string;
  columns: ColumnConfig[];
  /** Status do nível fora de qualquer coluna: os itens neles ficam invisíveis (UX/UI 6.2). */
  unmappedStatusKeys: string[];
}

export interface BoardView {
  boardId: string;
  level: string;
  unmappedStatusKeys: string[];
  columns: BoardColumn[];
}

/** Transição possível a partir do status atual (SSD 3.5). */
export interface TransitionOption {
  id: string;
  to: StatusRef;
  canExecute: boolean;
  failedRules: { ruleId: string; type: string; message: string }[];
}

const API = '/api/v1';

/** Quadros, artefatos e transições (SSD 3.4, 3.5 e 3.9). O workspace vai no cabeçalho, pelo interceptor. */
@Injectable({ providedIn: 'root' })
export class WorkService {
  private readonly http = inject(HttpClient);

  async boards(orgUnitId: string, level: string): Promise<Board[]> {
    const response = await firstValueFrom(
      this.http.get<{ items: Board[] }>(`${API}/boards`, { params: { orgUnitId, level } }),
    );
    return response.items;
  }

  createBoard(orgUnitId: string, level: string): Promise<Board> {
    return firstValueFrom(this.http.post<Board>(`${API}/boards`, { orgUnitId, level }));
  }

  updateBoard(id: string, body: { name?: string; columns?: ColumnConfig[] }): Promise<Board> {
    return firstValueFrom(this.http.patch<Board>(`${API}/boards/${id}`, body));
  }

  /** Status de um nível (chave e nome), a partir dos workflows dos tipos desse nível (SSD 3.3). */
  async levelStatuses(level: string): Promise<{ key: string; name: string }[]> {
    const [types, workflows] = await Promise.all([
      firstValueFrom(this.http.get<{ items: { key: string; level: string }[] }>(`${API}/artifact-types`)),
      firstValueFrom(
        this.http.get<{ items: { artifactTypes: string[]; statuses: { key: string; name: string }[] }[] }>(`${API}/workflows`),
      ),
    ]);
    const ofLevel = new Set(types.items.filter((t) => t.level === level).map((t) => t.key));
    const byKey = new Map<string, string>();
    for (const workflow of workflows.items.filter((w) => w.artifactTypes.some((t) => ofLevel.has(t)))) {
      workflow.statuses.forEach((s) => byKey.has(s.key) || byKey.set(s.key, s.name));
    }
    return [...byKey].map(([key, name]) => ({ key, name }));
  }

  /** Marca ou desmarca o bloqueio do item (SSD 3.4). */
  setBlocked(artifact: string, blocked: boolean, reason: string | null, version: number): Promise<Artifact> {
    return firstValueFrom(
      this.http.patch<Artifact>(`${API}/artifacts/${artifact}`, { blocked, blockedReason: reason, version }),
    );
  }

  boardView(id: string): Promise<BoardView> {
    return firstValueFrom(this.http.get<BoardView>(`${API}/boards/${id}/view`));
  }

  async artifacts(level: string): Promise<Artifact[]> {
    const response = await firstValueFrom(
      this.http.get<{ items: Artifact[] }>(`${API}/artifacts`, { params: { level, size: 100 } }),
    );
    return response.items;
  }

  createArtifact(body: { type: string; title: string; description?: string; orgUnitId: string }): Promise<Artifact> {
    return firstValueFrom(this.http.post<Artifact>(`${API}/artifacts`, body));
  }

  async transitions(artifact: string): Promise<TransitionOption[]> {
    const response = await firstValueFrom(
      this.http.get<{ items: TransitionOption[] }>(`${API}/artifacts/${artifact}/transitions`),
    );
    return response.items;
  }

  transition(artifact: string, transitionId: string, version: number): Promise<Artifact> {
    return firstValueFrom(this.http.post<Artifact>(`${API}/artifacts/${artifact}/transitions`, { transitionId, version }));
  }
}

/** Transição que leva a uma das chaves de status da coluna, se existir. */
export function transitionInto(options: TransitionOption[], column: BoardColumn): TransitionOption | undefined {
  return options.find((option) => column.statusKeys.includes(option.to.key));
}

/** Transições que o usuário pode executar agora: com permissão e sem regra pendente (gate ou WIP). */
export function executable(options: TransitionOption[]): TransitionOption[] {
  return options.filter((option) => option.canExecute && option.failedRules.length === 0);
}

/** Validação local da configuração, espelhando a da API, para avisar antes de salvar. */
export function configProblems(columns: ColumnConfig[]): string[] {
  const problems: string[] = [];
  if (columns.length === 0) {
    problems.push('O quadro precisa de ao menos uma coluna.');
  }
  let downstream = false;
  const used = new Set<string>();
  columns.forEach((column, index) => {
    const label = column.name.trim() || `coluna ${index + 1}`;
    if (!column.name.trim()) {
      problems.push(`A coluna ${index + 1} precisa de nome.`);
    }
    if (column.phase === 'UPSTREAM' && downstream) {
      problems.push(`"${label}" é Upstream e está depois de uma coluna Downstream.`);
    }
    downstream ||= column.phase === 'DOWNSTREAM';
    if (column.statusKeys.length === 0) {
      problems.push(`"${label}" precisa de ao menos um status.`);
    }
    column.statusKeys.forEach((key) => {
      if (used.has(key)) {
        problems.push(`O status ${key} está em mais de uma coluna.`);
      }
      used.add(key);
    });
    if (column.wipLimit !== null && column.wipLimit < 1) {
      problems.push(`O limite de WIP de "${label}" deve ser 1 ou mais (vazio é sem limite).`);
    }
  });
  return problems;
}
