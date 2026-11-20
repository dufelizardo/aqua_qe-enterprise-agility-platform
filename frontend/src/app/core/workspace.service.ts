import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

/** Unidade em que o usuário tem papel (GET /me/workspaces, SSD 3.1). */
export interface Workspace {
  id: string;
  name: string;
  level: string;
  roles: string[];
}

const STORAGE_KEY = 'aquaqe.workspace';

/**
 * Workspace ativo (UX/UI 7.1): a unidade em que o usuário está trabalhando. A escolha é lembrada no navegador e enviada
 * em X-Workspace-Id nas chamadas à API. Com uma única unidade, ela é escolhida sozinha.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);

  readonly workspaces = signal<Workspace[]>([]);
  readonly loaded = signal(false);
  private readonly currentId = signal<string | null>(null);
  readonly current = computed(() => this.workspaces().find((w) => w.id === this.currentId()) ?? null);
  /** O seletor do cabeçalho só aparece com mais de uma unidade (UX/UI 4). */
  readonly selectable = computed(() => this.workspaces().length > 1);

  async load(): Promise<void> {
    const response = await firstValueFrom(this.http.get<{ items: Workspace[] }>('/api/v1/me/workspaces'));
    this.workspaces.set(response.items);
    const remembered = readStorage();
    const chosen =
      response.items.find((w) => w.id === remembered) ?? (response.items.length === 1 ? response.items[0] : undefined);
    this.currentId.set(chosen?.id ?? null);
    this.loaded.set(true);
  }

  choose(id: string): void {
    this.currentId.set(id);
    writeStorage(id);
  }

  /** Id do workspace ativo, para o cabeçalho X-Workspace-Id. */
  currentWorkspaceId(): string | null {
    return this.current()?.id ?? null;
  }

  /** O usuário tem algum dos papéis no workspace ativo? Só decide o que mostrar; quem decide o acesso é a API. */
  hasRole(...roles: string[]): boolean {
    return this.current()?.roles.some((role) => roles.includes(role)) ?? false;
  }
}

/** Telas que listam itens exigem workspace; sem ele, o usuário vai à seleção (UX/UI 7.1). */
export const workspaceGuard: CanActivateFn = async (_route, state) => {
  const workspace = inject(WorkspaceService);
  const router = inject(Router);
  if (!workspace.loaded()) {
    await workspace.load();
  }
  return workspace.current() ? true : router.createUrlTree(['/workspace'], { queryParams: { voltar: state.url } });
};

/** Envia o workspace ativo em toda chamada à API (SSD 1.2). */
export const workspaceInterceptor: HttpInterceptorFn = (request, next) => {
  const id = inject(WorkspaceService).currentWorkspaceId();
  if (id && request.url.startsWith('/api/') && !request.headers.has('X-Workspace-Id')) {
    return next(request.clone({ setHeaders: { 'X-Workspace-Id': id } }));
  }
  return next(request);
};

function readStorage(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function writeStorage(id: string): void {
  try {
    localStorage.setItem(STORAGE_KEY, id);
  } catch {
    // Sem armazenamento (janela privada): a escolha vale só para esta sessão.
  }
}
