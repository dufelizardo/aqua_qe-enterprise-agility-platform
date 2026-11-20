import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { OrgLevel } from '../admin/organization.service';

export type FlightLevel = 'FL1' | 'FL2' | 'FL3';

/**
 * Níveis organizacionais da instalação (SSD 3.2; ADR-14). A interface nunca usa nomes de nível fixos no código
 * (Proposta 06, 14.1): o rótulo de cada tela de Flight Level é o nome do nível ligado a ele na configuração.
 */
@Injectable({ providedIn: 'root' })
export class LevelsService {
  private readonly http = inject(HttpClient);
  private loading: Promise<void> | null = null;

  readonly levels = signal<OrgLevel[]>([]);
  private readonly byFlightLevel = computed(
    () => new Map(this.levels().filter((l) => l.flightLevel).map((l) => [l.flightLevel as FlightLevel, l])),
  );

  load(): Promise<void> {
    this.loading ??= firstValueFrom(this.http.get<{ items: OrgLevel[] }>('/api/v1/org-levels'))
      .then((response) => this.levels.set(response.items))
      .catch(() => {
        this.loading = null;
      });
    return this.loading;
  }

  /** Nome do nível ligado ao Flight Level; enquanto não carrega (ou se não houver), o próprio Flight Level. */
  nameOf(flightLevel: FlightLevel): string {
    return this.byFlightLevel().get(flightLevel)?.name ?? flightLevel;
  }

  /** Nome de um nível pela chave (ex.: rótulo do nível de uma unidade). */
  nameOfKey(key: string): string {
    return this.levels().find((l) => l.key === key)?.name ?? key;
  }
}
