import { Component, inject, signal } from '@angular/core';

import { messageOf, toApiError } from '../../core/api-error';
import { Preferences as Prefs, PreferencesService, Theme } from '../../core/preferences.service';

/**
 * Preferências (#15; REV-10; UX/UI 7.14): nível Pessoal da configuração (UX/UI 7.11). Tema e avisos do sino; cada
 * mudança é gravada na hora. Idioma e widgets da Home ficam para depois.
 */
@Component({
  selector: 'app-preferences',
  template: `
    <h1 data-testid="page-title">Preferências</h1>
    <p class="muted">Valem só para você, em qualquer navegador.</p>

    <section class="card" aria-labelledby="pref-theme">
      <h2 id="pref-theme">Tema</h2>
      <div class="options" role="radiogroup" aria-labelledby="pref-theme">
        @for (option of themes; track option.value) {
          <label>
            <input
              type="radio"
              name="theme"
              [value]="option.value"
              [checked]="prefs().theme === option.value"
              [disabled]="saving()"
              (change)="setTheme(option.value)"
              [attr.data-testid]="'preferences-theme-' + option.value"
            />
            {{ option.label }}
          </label>
        }
      </div>
    </section>

    <section class="card" aria-labelledby="pref-notifications">
      <h2 id="pref-notifications">Avisos do sino</h2>
      <p class="muted">Desligado, o aviso não é criado. O comentário continua na página.</p>
      <label>
        <input
          type="checkbox"
          [checked]="prefs().notifications.mention"
          [disabled]="saving()"
          (change)="setNotification('mention', $any($event.target).checked)"
          data-testid="preferences-notify-mention"
        />
        Quando alguém me menciona em um comentário
      </label>
      <label>
        <input
          type="checkbox"
          [checked]="prefs().notifications.reply"
          [disabled]="saving()"
          (change)="setNotification('reply', $any($event.target).checked)"
          data-testid="preferences-notify-reply"
        />
        Quando alguém responde a um comentário meu
      </label>
    </section>

    <p class="status" role="status" aria-live="polite">
      @if (error()) {
        <span class="error" data-testid="preferences-error">{{ error() }}</span>
      } @else if (saved()) {
        <span data-testid="preferences-saved">Preferências salvas.</span>
      }
    </p>
  `,
  styles: `
    :host { display: block; max-width: 640px; }
    .muted { color: var(--text-muted); }
    .card { margin-top: 16px; padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
    h2 { margin: 0 0 8px; font-size: 16px; }
    .options { display: flex; flex-wrap: wrap; gap: 16px; }
    label { display: flex; gap: 8px; align-items: center; padding: 4px 0; cursor: pointer; }
    .status { min-height: 1.5em; }
    .error { color: var(--danger); }
  `,
})
export class PreferencesPage {
  private readonly service = inject(PreferencesService);

  protected readonly prefs = this.service.preferences;
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly themes: { value: Theme; label: string }[] = [
    { value: 'SYSTEM', label: 'Do sistema' },
    { value: 'LIGHT', label: 'Claro' },
    { value: 'DARK', label: 'Escuro' },
  ];

  constructor() {
    void this.service.load();
  }

  protected setTheme(theme: Theme): void {
    void this.save({ ...this.prefs(), theme });
  }

  protected setNotification(kind: 'mention' | 'reply', on: boolean): void {
    void this.save({ ...this.prefs(), notifications: { ...this.prefs().notifications, [kind]: on } });
  }

  private async save(next: Prefs): Promise<void> {
    this.saving.set(true);
    this.saved.set(false);
    this.error.set(null);
    try {
      await this.service.save(next);
      this.saved.set(true);
    } catch (err) {
      this.error.set(messageOf(toApiError(err)));
    } finally {
      this.saving.set(false);
    }
  }
}
