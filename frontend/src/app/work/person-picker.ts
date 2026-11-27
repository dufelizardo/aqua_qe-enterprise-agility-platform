import { ChangeDetectionStrategy, Component, effect, inject, input, model, signal } from '@angular/core';

import { PersonRef, WorkService } from './work.service';

/**
 * Busca de pessoa pelo nome para o formulário de item novo (#12; REV-07): quem tem papel na unidade, nas de cima e nas de
 * baixo ({@code GET /org-units/{id}/people}). {@code person} é a escolha; {@code null} quando não há.
 */
@Component({
  selector: 'app-person-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="person-picker" [attr.data-testid]="testId()">
      @if (person(); as chosen) {
        <div class="chosen">
          <span data-testid="person-picker-chosen">{{ chosen.name }}</span>
          <button type="button" class="link" (click)="clear()" data-testid="person-picker-clear">Trocar</button>
        </div>
      } @else {
        <input
          type="search"
          [id]="inputId()"
          [value]="query()"
          (input)="search(value($event))"
          (focus)="search(query())"
          (keydown.escape)="results.set(null)"
          placeholder="Buscar pelo nome"
          autocomplete="off"
          data-testid="person-picker-search"
        />
        @if (results(); as list) {
          <ul class="person-options" role="listbox">
            @for (p of list; track p.id) {
              <li>
                <button type="button" class="link" (click)="choose(p)" data-testid="person-picker-option">{{ p.name }}</button>
              </li>
            } @empty {
              <li class="hint" data-testid="person-picker-empty">Ninguém encontrado.</li>
            }
          </ul>
        }
      }
    </div>
  `,
  styles: `
    .person-picker { display: grid; gap: 4px; }
    .chosen { display: flex; gap: 8px; align-items: center; }
    .person-options { display: grid; gap: 2px; max-height: 200px; margin: 0; padding: 4px 0; overflow: auto; list-style: none; }
    .person-options .link { width: 100%; text-align: left; }
  `,
})
export class PersonPicker {
  private readonly work = inject(WorkService);

  readonly unitId = input.required<string>();
  readonly artifactType = input.required<string>();
  readonly inputId = input<string>('');
  readonly testId = input<string>('person-picker');
  readonly person = model<PersonRef | null>(null);

  protected readonly query = signal('');
  protected readonly results = signal<PersonRef[] | null>(null);
  private seq = 0;

  constructor() {
    // Outra unidade ou outro tipo: a escolha e a busca anteriores deixam de valer.
    effect(() => {
      this.unitId();
      this.artifactType();
      this.seq++;
      this.query.set('');
      this.results.set(null);
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected async search(q: string): Promise<void> {
    this.query.set(q);
    const unit = this.unitId();
    const type = this.artifactType();
    if (!unit || !type) {
      return;
    }
    const seq = ++this.seq;
    try {
      const people = await this.work.peopleForNew(unit, type, q);
      if (seq === this.seq) {
        this.results.set(people);
      }
    } catch {
      if (seq === this.seq) {
        this.results.set([]);
      }
    }
  }

  protected choose(p: PersonRef): void {
    this.person.set(p);
    this.query.set('');
    this.results.set(null);
  }

  protected clear(): void {
    this.person.set(null);
  }
}
