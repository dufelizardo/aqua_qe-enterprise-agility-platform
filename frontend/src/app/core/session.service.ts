import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Resposta de GET /api/v1/me (SSD, seção 3.1). */
export interface CurrentUser {
  id: string;
  username: string;
  name: string | null;
  email: string | null;
  assignments: unknown[];
}

/** Persona do modo sem senha do ambiente dev (ADR-17). */
export interface DevPersona {
  username: string;
  name: string;
  email: string;
  role: string;
}

/** Início do login OIDC conduzido pelo backend (ADR-16). */
export const LOGIN_URL = '/oauth2/authorization/keycloak';

/**
 * Sessão do usuário. O login é feito pelo backend (BFF): o navegador só guarda o cookie de sessão e nunca vê tokens.
 * A landing é pública; as telas internas exigem sessão (authGuard), e sem ela o usuário vai ao login.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  readonly user = signal<CurrentUser | null>(null);
  /** Personas disponíveis; só preenchido no ambiente dev, sem senha (ADR-17). */
  readonly devPersonas = signal<DevPersona[]>([]);
  readonly devMode = computed(() => this.devPersonas().length > 0);
  readonly authenticated = computed(() => this.user() !== null);
  readonly displayName = computed(() => this.user()?.name ?? this.user()?.username ?? '');
  readonly initials = computed(() =>
    this.displayName()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]!.toUpperCase())
      .join(''),
  );

  /** Carrega o usuário corrente; sem sessão (401) fica anônimo, sem redirecionar. */
  async load(): Promise<void> {
    try {
      this.user.set(await firstValueFrom(this.http.get<CurrentUser>('/api/v1/me')));
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.user.set(null);
        return;
      }
      throw error;
    }
    await this.loadDevPersonas();
  }

  /** No ambiente dev a API expõe as personas; nos demais o caminho não existe (404). */
  private async loadDevPersonas(): Promise<void> {
    try {
      const response = await firstValueFrom(
        this.http.get<{ current: string; personas: DevPersona[] }>('/api/v1/dev/personas'),
      );
      this.devPersonas.set(response.personas);
    } catch {
      this.devPersonas.set([]);
    }
  }

  /** Troca a persona do ambiente dev e recarrega a tela com o novo usuário. */
  async switchPersona(username: string): Promise<void> {
    await firstValueFrom(this.http.put('/api/v1/dev/persona', { username }));
    await this.load();
  }

  login(): void {
    this.redirect(LOGIN_URL);
  }

  async logout(): Promise<void> {
    const response = await firstValueFrom(this.http.post<{ redirectUrl: string }>('/logout', null));
    this.redirect(response.redirectUrl);
  }

  /** Separado para os testes poderem interceptar a navegação. */
  redirect(url: string): void {
    window.location.assign(url);
  }
}
