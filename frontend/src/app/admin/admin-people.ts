import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { messageOf, toApiError } from '../core/api-error';
import { PeopleService, PersonAccount, confirmsName, personalDataUrl } from './people.service';

/**
 * Administração › Pessoas e privacidade (#32; REV-14; UX/UI 7.10): achar uma pessoa, baixar os dados dela em JSON e
 * anonimizar a conta de quem saiu da organização. Só o administrador da plataforma na unidade raiz.
 */
@Component({
  selector: 'app-admin-people',
  imports: [DatePipe, MatSnackBarModule, RouterLink],
  template: `
    <header class="page-header">
      <p class="eyebrow">Administração</p>
      <nav class="admin-tabs" aria-label="Seções da administração">
        <a routerLink="/admin/organizacao">Estrutura organizacional</a>
        <a routerLink="/admin/workflows">Workflows</a>
        <a routerLink="/admin/gates">Gates</a>
        <a routerLink="/admin/campos-customizados">Campos customizados</a>
        <a routerLink="/admin/responsavel-padrao">Responsável padrão</a>
        <a routerLink="/admin/templates-de-quadro">Templates de quadro</a>
        <a routerLink="/admin/pessoas" class="on" aria-current="page">Pessoas e privacidade</a>
      </nav>
      <h1 data-testid="page-title">Pessoas e privacidade</h1>
      <p class="lead">
        Os dados de cada pessoa, para atender a um pedido dela, e a anonimização de quem saiu da organização. As duas
        ações ficam na trilha de auditoria.
      </p>
    </header>

    <section class="card">
      <form class="search" (submit)="search($event)" role="search">
        <label class="field">
          Buscar pessoa
          <input
            [value]="query()"
            (input)="query.set(value($event))"
            placeholder="Nome, usuário ou e-mail"
            data-testid="people-search"
          />
        </label>
        <button type="submit" class="btn primary" [disabled]="busy()" data-testid="people-search-submit">Buscar</button>
      </form>

      @if (error()) {
        <p class="error" role="alert" data-testid="people-error">{{ error() }}</p>
      }
      @if (people() === null) {
        <p class="muted">Busque pelo nome, pelo usuário ou pelo e-mail.</p>
      } @else if (people()!.length === 0) {
        <p class="muted" data-testid="people-empty">Ninguém encontrado.</p>
      } @else {
        <table class="people">
          <thead>
            <tr><th>Pessoa</th><th>E-mail</th><th>Última atividade</th><th><span class="sr-only">Ações</span></th></tr>
          </thead>
          <tbody>
            @for (person of people(); track person.id) {
              <tr data-testid="people-row" [attr.data-name]="person.name">
                <td>
                  <strong>{{ person.name }}</strong>
                  @if (person.username) {
                    <span class="muted"> · {{ person.username }}</span>
                  }
                  @if (person.anonymized) {
                    <span class="badge" data-testid="people-anonymized">Anonimizada</span>
                  }
                </td>
                <td>{{ person.email ?? '—' }}</td>
                <td>{{ person.lastSeenAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td class="actions">
                  <a class="btn" [href]="dataUrl(person.id)" download data-testid="people-export">Baixar dados</a>
                  @if (!person.anonymized) {
                    <button type="button" class="btn subtle" (click)="ask(person)" data-testid="people-anonymize">
                      Anonimizar
                    </button>
                  }
                </td>
              </tr>
              @if (confirming()?.id === person.id) {
                <tr class="confirm-row">
                  <td colspan="4">
                    <form class="confirm" (submit)="anonymize($event, person)" data-testid="people-anonymize-form">
                      <p>
                        A conta fica inativa e perde os papéis. Nome, usuário e e-mail saem; visitas, favoritos, avisos e
                        preferências são apagados. O conteúdo que a pessoa criou fica, com o nome "Pessoa removida".
                        <strong>Não dá para desfazer.</strong>
                      </p>
                      <label class="field">
                        Para confirmar, digite o nome: {{ person.name }}
                        <input
                          [value]="typed()"
                          (input)="typed.set(value($event))"
                          (keydown.escape)="confirming.set(null)"
                          data-testid="people-anonymize-name"
                        />
                      </label>
                      <div class="row">
                        <button
                          type="submit"
                          class="btn danger"
                          [disabled]="busy() || !matches(person)"
                          data-testid="people-anonymize-confirm"
                        >
                          Anonimizar
                        </button>
                        <button type="button" class="btn" (click)="confirming.set(null)">Cancelar</button>
                      </div>
                    </form>
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: `
    .page-header { margin-bottom: 16px; }
    .eyebrow { margin: 0; font-size: 12px; font-weight: 700; text-transform: uppercase; color: var(--text-secondary); }
    h1 { margin: 4px 0; font-size: 30px; font-weight: 800; }
    .lead { margin: 0; color: var(--text-secondary); max-width: 760px; }
    .admin-tabs { display: flex; flex-wrap: wrap; gap: 12px; margin: 6px 0; }
    .admin-tabs a { color: var(--text-secondary); text-decoration: none; font-weight: 600; }
    .admin-tabs a.on { color: var(--text); border-bottom: 2px solid var(--navy-900, currentColor); }
    .card { padding: 16px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface); }
    .search { display: flex; flex-wrap: wrap; gap: 8px; align-items: end; }
    .field { display: grid; gap: 4px; font-size: 13px; font-weight: 600; max-width: 420px; flex: 1 1 260px; }
    input { min-height: 32px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; background: var(--surface); color: var(--text); }
    .people { width: 100%; margin-top: 12px; border-collapse: collapse; }
    .people th { text-align: left; font-size: 12px; color: var(--text-secondary); padding: 6px 8px; border-bottom: 1px solid var(--border); }
    .people td { padding: 8px; border-bottom: 1px solid var(--border); vertical-align: top; }
    .actions { white-space: nowrap; text-align: right; }
    .actions .btn + .btn { margin-left: 6px; }
    .badge { margin-left: 6px; padding: 1px 6px; border-radius: 999px; font-size: 11px; background: var(--surface-muted); color: var(--text-secondary); }
    .confirm p { margin: 0 0 8px; max-width: 760px; }
    .row { display: flex; gap: 6px; margin-top: 8px; }
    .btn.danger { background: var(--danger); color: #fff; border-color: var(--danger); }
    .muted { color: var(--text-secondary); font-size: 13px; }
    .error { color: var(--danger); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    @media (max-width: 700px) { .people thead { display: none; } .people td { display: block; border: 0; padding: 4px 0; } .people tr { display: block; padding: 8px 0; border-bottom: 1px solid var(--border); } .actions { text-align: left; } }
  `,
})
export class AdminPeople {
  private readonly api = inject(PeopleService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly query = signal('');
  protected readonly people = signal<PersonAccount[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly confirming = signal<PersonAccount | null>(null);
  protected readonly typed = signal('');

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected dataUrl(id: string): string {
    return personalDataUrl(id);
  }

  protected matches(person: PersonAccount): boolean {
    return confirmsName(this.typed(), person.name);
  }

  protected async search(event: Event): Promise<void> {
    event.preventDefault();
    this.busy.set(true);
    this.error.set(null);
    this.confirming.set(null);
    try {
      this.people.set(await this.api.search(this.query().trim()));
    } catch (err) {
      this.error.set(messageOf(toApiError(err)));
    } finally {
      this.busy.set(false);
    }
  }

  protected ask(person: PersonAccount): void {
    this.typed.set('');
    this.confirming.set(person);
  }

  protected async anonymize(event: Event, person: PersonAccount): Promise<void> {
    event.preventDefault();
    if (!this.matches(person)) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const result = await this.api.anonymize(person.id);
      this.people.update((list) =>
        (list ?? []).map((p) =>
          p.id === person.id ? { ...p, name: result.name, username: null, email: null, active: false, anonymized: true } : p,
        ),
      );
      this.confirming.set(null);
      this.snackBar.open('Conta anonimizada.', 'OK', { duration: 4000 });
    } catch (err) {
      this.error.set(messageOf(toApiError(err)));
    } finally {
      this.busy.set(false);
    }
  }
}
