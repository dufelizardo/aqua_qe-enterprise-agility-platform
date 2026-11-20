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
}

export interface BoardColumn {
  id: string;
  name: string;
  phase: 'UPSTREAM' | 'DOWNSTREAM';
  statusKeys: string[];
  cards: Card[];
}

export interface Board {
  id: string;
  orgUnitId: string;
  level: string;
  name: string;
}

export interface BoardView {
  boardId: string;
  level: string;
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
