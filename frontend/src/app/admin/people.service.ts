import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

const API = '/api/v1/admin/people';

/** Pessoa no diretório da Administração (#32; REV-14; SSD 2.2). Só o administrador vê o e-mail. */
export interface PersonAccount {
  id: string;
  name: string;
  username: string | null;
  email: string | null;
  active: boolean;
  anonymized: boolean;
  lastSeenAt: string;
}

/** Endereço do JSON com os dados da pessoa, para baixar. */
export function personalDataUrl(id: string): string {
  return `${API}/${encodeURIComponent(id)}/personal-data`;
}

/** A confirmação da anonimização exige digitar o nome atual, sem diferenciar maiúsculas nem espaços nas pontas. */
export function confirmsName(typed: string, name: string): boolean {
  return typed.trim().toLocaleLowerCase('pt-BR') === name.trim().toLocaleLowerCase('pt-BR') && name.trim() !== '';
}

@Injectable({ providedIn: 'root' })
export class PeopleService {
  private readonly http = inject(HttpClient);

  async search(q: string): Promise<PersonAccount[]> {
    const result = await firstValueFrom(this.http.get<{ items: PersonAccount[] }>(API, { params: { q } }));
    return result.items;
  }

  anonymize(id: string): Promise<{ id: string; name: string; active: boolean; anonymized: boolean }> {
    return firstValueFrom(this.http.post<{ id: string; name: string; active: boolean; anonymized: boolean }>(
      `${API}/${encodeURIComponent(id)}/anonymize`, null));
  }
}
