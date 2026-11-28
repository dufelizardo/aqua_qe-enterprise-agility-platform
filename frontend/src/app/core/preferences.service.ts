import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Tema escolhido nas Preferências (#15; REV-10). SYSTEM segue o sistema operacional. */
export type Theme = 'SYSTEM' | 'LIGHT' | 'DARK';

/** Resposta de GET/PUT /api/v1/me/preferences (SSD 3.1). */
export interface Preferences {
  theme: Theme;
  notifications: { mention: boolean; reply: boolean };
}

export const DEFAULT_PREFERENCES: Preferences = { theme: 'SYSTEM', notifications: { mention: true, reply: true } };

/** Cópia do tema no navegador, para a tela abrir já no tema certo antes da resposta do servidor. */
const THEME_KEY = 'aquaqe.theme';

/** Tema efetivo: SYSTEM vira claro ou escuro conforme o sistema. */
export function resolveTheme(theme: Theme, systemDark: boolean): 'light' | 'dark' {
  if (theme === 'SYSTEM') {
    return systemDark ? 'dark' : 'light';
  }
  return theme === 'DARK' ? 'dark' : 'light';
}

/**
 * Preferências pessoais (#15): tema e avisos do sino, gravados no servidor. A Shell chama {@link start} para aplicar o
 * tema nas telas internas; ao sair, {@link stop} devolve o tema claro (a landing é sempre clara).
 */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly http = inject(HttpClient);

  readonly preferences = signal<Preferences>({ ...DEFAULT_PREFERENCES, theme: cachedTheme() });
  private media: MediaQueryList | null = null;
  private readonly onSystemChange = () => this.apply();

  /** Aplica o tema guardado, acompanha o sistema e carrega as preferências do servidor. */
  start(destroyRef: DestroyRef): void {
    this.media = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;
    this.media?.addEventListener('change', this.onSystemChange);
    destroyRef.onDestroy(() => this.stop());
    this.apply();
    void this.load();
  }

  stop(): void {
    this.media?.removeEventListener('change', this.onSystemChange);
    this.media = null;
    document.documentElement.removeAttribute('data-theme');
  }

  async load(): Promise<void> {
    try {
      this.set(await firstValueFrom(this.http.get<Preferences>('/api/v1/me/preferences')));
    } catch {
      // Sem resposta, ficam os padrões e o tema guardado.
    }
  }

  async save(preferences: Preferences): Promise<Preferences> {
    const saved = await firstValueFrom(this.http.put<Preferences>('/api/v1/me/preferences', preferences));
    this.set(saved);
    return saved;
  }

  private set(preferences: Preferences): void {
    this.preferences.set(preferences);
    try {
      localStorage.setItem(THEME_KEY, preferences.theme);
    } catch {
      // Sem armazenamento local, o tema vem do servidor a cada carga.
    }
    this.apply();
  }

  private apply(): void {
    const theme = resolveTheme(this.preferences().theme, this.media?.matches ?? false);
    document.documentElement.setAttribute('data-theme', theme);
  }
}

function cachedTheme(): Theme {
  try {
    const value = localStorage.getItem(THEME_KEY);
    return value === 'LIGHT' || value === 'DARK' ? value : 'SYSTEM';
  } catch {
    return 'SYSTEM';
  }
}
