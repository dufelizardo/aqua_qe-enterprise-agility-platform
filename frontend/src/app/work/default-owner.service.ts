import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ROLE_LABELS } from './gates.service';

/** Quem fica como Owner do artefato criado sem um (#43). */
export type OwnerMode = 'NONE' | 'CREATOR' | 'ROLE';

export interface OwnerRule {
  artifactType: { key: string; name: string; level: string };
  mode: OwnerMode;
  role: string | null;
  /** A regra vem de outra unidade (ou é o padrão, sem origem). */
  inherited: boolean;
  source: { id: string; name: string } | null;
}

export interface OwnerSuggestion {
  mode: OwnerMode;
  role: string | null;
  source: { id: string; name: string } | null;
  candidates: { id: string; name: string }[];
  /** Preenchido quando a regra indica uma pessoa só. */
  suggestedId: string | null;
}

export const OWNER_MODE_LABELS: Record<OwnerMode, string> = {
  NONE: 'Sem responsável',
  CREATOR: 'Quem criou',
  ROLE: 'Pessoa com o papel',
};

/** Papéis que podem ser o responsável padrão: todos, menos o administrador da plataforma. */
export const OWNER_ROLES = Object.keys(ROLE_LABELS).filter((role) => role !== 'PLATFORM_ADMIN');

/** Resumo da regra para a tabela: "Pessoa com o papel: Business Owner". */
export function ruleSummary(rule: Pick<OwnerRule, 'mode' | 'role'>): string {
  if (rule.mode === 'ROLE') {
    return `${OWNER_MODE_LABELS.ROLE}: ${ROLE_LABELS[rule.role ?? ''] ?? rule.role}`;
  }
  return OWNER_MODE_LABELS[rule.mode];
}

/**
 * O que mandar em {@code ownerId} ao criar: a escolha do formulário, {@code null} para "sem Owner" e {@code undefined}
 * (campo omitido) quando não há escolha, deixando a regra decidir no servidor.
 */
export function ownerIdToSend(choice: string): string | null | undefined {
  if (choice === 'none') {
    return null;
  }
  return choice === '' ? undefined : choice;
}

const API = '/api/v1';

/** Endpoints do responsável padrão por unidade e tipo (#43; SSD 3.4). */
@Injectable({ providedIn: 'root' })
export class DefaultOwnerService {
  private readonly http = inject(HttpClient);

  async rules(unitId: string): Promise<OwnerRule[]> {
    return (await firstValueFrom(this.http.get<{ items: OwnerRule[] }>(`${API}/org-units/${unitId}/default-owner-rules`)))
      .items;
  }

  save(unitId: string, typeKey: string, mode: OwnerMode, role: string | null): Promise<OwnerRule> {
    const body = mode === 'ROLE' ? { mode, role } : { mode };
    return firstValueFrom(this.http.put<OwnerRule>(`${API}/org-units/${unitId}/default-owner-rules/${typeKey}`, body));
  }

  remove(unitId: string, typeKey: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API}/org-units/${unitId}/default-owner-rules/${typeKey}`));
  }

  suggestion(unitId: string, typeKey: string): Promise<OwnerSuggestion> {
    return firstValueFrom(
      this.http.get<OwnerSuggestion>(`${API}/org-units/${unitId}/default-owner`, { params: { artifactType: typeKey } }),
    );
  }
}
