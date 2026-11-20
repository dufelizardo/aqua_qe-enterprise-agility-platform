import { HttpErrorResponse } from '@angular/common/http';

/** Erro da API no formato Problem Details com `code` e `errors` (SSD, seção 1.6). */
export interface ApiError {
  status: number;
  code: string;
  detail: string;
  errors: { field: string; message: string }[];
}

/** Nomes dos campos nas mensagens de validação. */
const FIELD_LABELS: Record<string, string> = {
  name: 'Nome',
  level: 'Nível',
  parentId: 'Unidade pai',
  targetId: 'Unidade',
  active: 'Situação',
};

/** Mensagens por código (UX/UI, seção 9); `VALIDATION_FAILED` usa as mensagens por campo. */
const MESSAGES: Record<string, string> = {
  FORBIDDEN: 'Você não tem permissão para esta ação.',
  NOT_FOUND: 'Item não encontrado ou sem acesso.',
  VERSION_CONFLICT: 'Este item foi alterado por outra pessoa.',
  UNAUTHENTICATED: 'Sua sessão expirou.',
};

export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object' && 'code' in error.error) {
    const body = error.error as Partial<ApiError>;
    return {
      status: error.status,
      code: body.code ?? 'UNKNOWN',
      detail: body.detail ?? '',
      errors: body.errors ?? [],
    };
  }
  const status = error instanceof HttpErrorResponse ? error.status : 0;
  return { status, code: 'UNKNOWN', detail: 'Não foi possível concluir. Tente novamente.', errors: [] };
}

/** Texto para o usuário, seguindo a UX/UI, seção 9. */
export function messageOf(error: ApiError): string {
  if (error.code === 'VALIDATION_FAILED' && error.errors.length > 0) {
    return error.errors.map((e) => `${FIELD_LABELS[e.field] ?? e.field}: ${e.message}`).join('; ');
  }
  return MESSAGES[error.code] ?? (error.detail || 'Não foi possível concluir. Tente novamente.');
}

/** Mensagem do campo, quando a API marcou esse campo. */
export function fieldError(error: ApiError | null, field: string): string | null {
  return error?.errors.find((e) => e.field === field)?.message ?? null;
}
