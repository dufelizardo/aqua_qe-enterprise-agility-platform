import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Tipo de um campo customizado (#17; REV-12). */
export type CustomFieldType = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECT';

/** Campo customizado declarado num tipo de artefato. A chave e o tipo não mudam depois de criados. */
export interface CustomField {
  id: string;
  artifactType: string;
  key: string;
  label: string;
  type: CustomFieldType;
  options: string[];
  position: number;
}

export const FIELD_TYPE_LABELS: Record<CustomFieldType, string> = {
  TEXT: 'Texto',
  NUMBER: 'Número',
  DATE: 'Data',
  BOOLEAN: 'Sim/Não',
  SELECT: 'Lista de opções',
};

/** Chave a partir do rótulo, no formato aceito pela API: "Canal de venda" → "canalDeVenda". */
export function keyFromLabel(label: string): string {
  const words = label
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^A-Za-z0-9]+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean);
  const key = words
    .map((w, i) => (i === 0 ? w.toLowerCase() : w[0]!.toUpperCase() + w.slice(1).toLowerCase()))
    .join('');
  // A chave começa com letra minúscula: "2ª revisão" vira "campo2Revisao".
  return (/^[a-z]/.test(key) ? key : `campo${key}`).slice(0, 40);
}

/** Valor para leitura: "Sim"/"Não", data em dd/mm/aaaa, "—" se vazio. */
export function formatValue(field: Pick<CustomField, 'type'>, value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  switch (field.type) {
    case 'BOOLEAN':
      return value ? 'Sim' : 'Não';
    case 'DATE': {
      const [y, m, d] = String(value).split('-');
      return y && m && d ? `${d}/${m}/${y}` : String(value);
    }
    case 'NUMBER':
      return Number(value).toLocaleString('pt-BR');
    default:
      return String(value);
  }
}

/** Valor do formulário para a API; texto vazio vira null (limpa o campo). */
export function parseInput(field: Pick<CustomField, 'type'>, raw: string): unknown {
  const text = raw.trim();
  if (!text) {
    return null;
  }
  switch (field.type) {
    case 'NUMBER': {
      const n = Number(text.replace(',', '.'));
      return Number.isFinite(n) ? n : text;
    }
    case 'BOOLEAN':
      return text === 'true';
    default:
      return text;
  }
}

/** Opções da lista, separadas por vírgula ou por linha, sem repetir. */
export function parseOptions(text: string): string[] {
  return [...new Set(text.split(/[,\n]/).map((o) => o.trim()).filter(Boolean))];
}

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class CustomFieldsService {
  private readonly http = inject(HttpClient);

  async list(artifactType?: string): Promise<CustomField[]> {
    const params: Record<string, string> = artifactType ? { artifactType } : {};
    return (await firstValueFrom(this.http.get<{ items: CustomField[] }>(`${API}/custom-fields`, { params }))).items;
  }

  create(artifactType: string, field: { key: string; label: string; type: CustomFieldType; options: string[] }): Promise<CustomField> {
    return firstValueFrom(
      this.http.post<CustomField>(`${API}/artifact-types/${artifactType}/custom-fields`, {
        key: field.key,
        label: field.label.trim(),
        type: field.type,
        options: field.type === 'SELECT' ? field.options : [],
      }),
    );
  }

  update(id: string, change: { label?: string; options?: string[]; position?: number }): Promise<CustomField> {
    return firstValueFrom(this.http.patch<CustomField>(`${API}/custom-fields/${id}`, change));
  }

  remove(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API}/custom-fields/${id}`));
  }
}
