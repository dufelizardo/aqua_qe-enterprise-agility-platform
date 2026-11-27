import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { WipPolicy } from './work.service';

/** Templates de quadro (#50; Proposta 05, MDL-07): catálogo único, aplicado por cópia ao criar o quadro. */
export interface BoardTemplate {
  id: string;
  name: string;
  level: 'FL1' | 'FL2' | 'FL3';
  description: string | null;
  columns: {
    name: string;
    phase: 'UPSTREAM' | 'DOWNSTREAM';
    statusKeys: string[];
    wipLimit: number | null;
    wipPolicy: WipPolicy;
  }[];
  /** Vem com a instalação. */
  builtin: boolean;
  updatedAt: string;
}

/** Resumo das colunas para o catálogo: "Triagem → Em atendimento (WIP 5) → Validando → Resolvido". */
export function columnsSummary(template: Pick<BoardTemplate, 'columns'>): string {
  return template.columns.map((c) => (c.wipLimit ? `${c.name} (WIP ${c.wipLimit})` : c.name)).join(' → ');
}

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class BoardTemplatesService {
  private readonly http = inject(HttpClient);

  async list(level?: string): Promise<BoardTemplate[]> {
    const params: Record<string, string> = level ? { level } : {};
    return (await firstValueFrom(this.http.get<{ items: BoardTemplate[] }>(`${API}/board-templates`, { params }))).items;
  }

  /** Salva as colunas já gravadas do quadro como template. */
  saveFromBoard(boardId: string, name: string, description: string): Promise<BoardTemplate> {
    return firstValueFrom(
      this.http.post<BoardTemplate>(`${API}/boards/${boardId}/template`, {
        name: name.trim(),
        description: description.trim() || null,
      }),
    );
  }

  update(id: string, name: string, description: string): Promise<BoardTemplate> {
    return firstValueFrom(
      this.http.patch<BoardTemplate>(`${API}/board-templates/${id}`, {
        name: name.trim(),
        description: description.trim() || null,
      }),
    );
  }

  remove(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API}/board-templates/${id}`));
  }
}
