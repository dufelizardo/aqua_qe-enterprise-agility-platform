import { Component, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { OrganizationService } from '../admin/organization.service';
import { messageOf, toApiError } from '../core/api-error';
import { WorkspaceService } from '../core/workspace.service';
import { Artifact, Sprint, WorkService, unitsOfLevelIn } from './work.service';

/**
 * Aba Backlog da tela de FL1 (UX/UI 7.7): itens ainda não iniciados, na ordem do backlog (rank, SSD 3.4). Subir e descer
 * reordena; "Incluir na sprint" põe o item numa sprint planejada ou ativa da squad dele (SSD 3.9).
 */
@Component({
  selector: 'app-squad-backlog',
  imports: [MatSnackBarModule],
  template: `
    <section class="panel" data-testid="backlog-panel">
      <p class="hint">Itens ainda não iniciados, na ordem de prioridade. A ordem vale para todo o produto.</p>
      <ul class="list" data-testid="backlog-list">
        @for (item of items(); track item.id; let i = $index; let last = $last) {
          <li class="backlog-row" data-testid="backlog-item">
            <span class="mono">{{ item.key }}</span>
            <span class="title">{{ item.title }}</span>
            <span class="muted">{{ item.orgUnit.name }}</span>
            @if (canEdit()) {
              <button type="button" class="icon" [disabled]="busy() || i === 0" (click)="up(i)" [attr.aria-label]="'Subir ' + item.key" data-testid="backlog-up">↑</button>
              <button type="button" class="icon" [disabled]="busy() || last" (click)="down(i)" [attr.aria-label]="'Descer ' + item.key" data-testid="backlog-down">↓</button>
            }
            @if (canManage() && sprintsOf(item).length > 0) {
              <select
                aria-label="Incluir na sprint"
                (change)="include(item, $any($event.target).value); $any($event.target).value = ''"
                data-testid="backlog-sprint"
              >
                <option value="">Incluir na sprint…</option>
                @for (sprint of sprintsOf(item); track sprint.id) {
                  <option [value]="sprint.id">{{ sprint.name }}{{ sprint.state === 'ACTIVE' ? ' (ativa)' : '' }}</option>
                }
              </select>
            }
          </li>
        } @empty {
          <li class="muted">Nenhum item aguardando no backlog.</li>
        }
      </ul>
    </section>
  `,
  styleUrl: './squad-panels.scss',
})
export class SquadBacklog {
  private readonly work = inject(WorkService);
  private readonly organization = inject(OrganizationService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly workspace = inject(WorkspaceService);

  protected readonly items = signal<Artifact[]>([]);
  /** Sprints planejadas e ativas de cada squad do workspace. */
  private readonly openSprints = signal<Map<string, Sprint[]>>(new Map());
  protected readonly busy = signal(false);

  protected readonly canEdit = computed(() =>
    this.workspace.hasRole('PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD', 'UX_DESIGNER', 'QA_ENGINEER', 'SQUAD_MEMBER'),
  );
  protected readonly canManage = computed(() => this.workspace.hasRole('PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD'));

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    const units = unitsOfLevelIn(await this.organization.allUnits(), this.workspace.currentWorkspaceId(), 'FL1');
    const sprints = new Map<string, Sprint[]>();
    await Promise.all(
      units.map(async (unit) =>
        sprints.set(unit.id, (await this.work.sprints(unit.id)).filter((s) => s.state !== 'CLOSED')),
      ),
    );
    this.openSprints.set(sprints);
    this.items.set(await this.work.backlog());
  }

  protected sprintsOf(item: Artifact): Sprint[] {
    return this.openSprints().get(item.orgUnit.id) ?? [];
  }

  protected up(index: number): Promise<void> {
    const list = this.items();
    return this.move(list[index]!, { beforeId: list[index - 1]!.id });
  }

  protected down(index: number): Promise<void> {
    const list = this.items();
    return this.move(list[index]!, { afterId: list[index + 1]!.id });
  }

  protected async include(item: Artifact, sprintId: string): Promise<void> {
    if (!sprintId) {
      return;
    }
    await this.run(async () => {
      await this.work.addToSprint(sprintId, item.id);
      this.toast(`${item.key} incluído na sprint.`);
    });
  }

  private async move(item: Artifact, position: { beforeId?: string; afterId?: string }): Promise<void> {
    await this.run(async () => {
      await this.work.rank(item.id, position);
      this.items.set(await this.work.backlog());
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
      await this.load();
    } catch (error) {
      this.toast(messageOf(toApiError(error)));
    } finally {
      this.busy.set(false);
    }
  }

  private toast(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000, horizontalPosition: 'end', verticalPosition: 'bottom' });
  }
}
