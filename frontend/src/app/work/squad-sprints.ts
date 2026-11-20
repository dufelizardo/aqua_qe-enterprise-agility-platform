import { Component, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { OrgUnit, OrganizationService } from '../admin/organization.service';
import { ApiError, messageOf, toApiError } from '../core/api-error';
import { LevelsService } from '../core/levels.service';
import { WorkspaceService } from '../core/workspace.service';
import { Artifact, Sprint, WorkService, unitsOfLevelIn } from './work.service';

/**
 * Aba Sprints da tela de FL1 (UX/UI 7.7; SSD 3.9): sprints opcionais por squad. Criar, iniciar, encerrar (avisando
 * quantos itens não concluídos voltarão ao backlog) e retirar itens. Squads em fluxo contínuo não criam sprints.
 */
@Component({
  selector: 'app-squad-sprints',
  imports: [MatSnackBarModule],
  templateUrl: './squad-sprints.html',
  styleUrl: './squad-panels.scss',
})
export class SquadSprints {
  private readonly work = inject(WorkService);
  private readonly organization = inject(OrganizationService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly workspace = inject(WorkspaceService);
  protected readonly levels = inject(LevelsService);

  protected readonly squads = signal<OrgUnit[]>([]);
  protected readonly squadId = signal('');
  protected readonly sprints = signal<Sprint[]>([]);
  protected readonly openSprint = signal<string | null>(null);
  protected readonly items = signal<Artifact[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<ApiError | null>(null);
  /** Encerramento pendente de confirmação, com quantos itens voltarão ao backlog. */
  protected readonly closing = signal<{ sprint: Sprint; returning: number } | null>(null);

  protected readonly newName = signal('');
  protected readonly newGoal = signal('');
  protected readonly newStart = signal('');
  protected readonly newEnd = signal('');

  /** Só decide o que mostrar; quem decide é a API ("gerir quadros e sprints", SSD 2). */
  protected readonly canManage = computed(() => this.workspace.hasRole('PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD'));
  protected readonly messageOf = messageOf;

  constructor() {
    void this.init();
  }

  private async init(): Promise<void> {
    const units = await this.organization.allUnits();
    const squads = unitsOfLevelIn(units, this.workspace.currentWorkspaceId(), 'FL1');
    this.squads.set(squads);
    if (squads.length > 0) {
      await this.chooseSquad(squads[0]!.id);
    }
  }

  protected async chooseSquad(id: string): Promise<void> {
    this.squadId.set(id);
    this.openSprint.set(null);
    this.sprints.set(await this.work.sprints(id));
  }

  protected async toggle(sprint: Sprint): Promise<void> {
    if (this.openSprint() === sprint.id) {
      this.openSprint.set(null);
      return;
    }
    this.openSprint.set(sprint.id);
    this.items.set(await this.work.sprintItems(sprint.id));
  }

  protected async create(): Promise<void> {
    await this.run(async () => {
      await this.work.createSprint({
        squadId: this.squadId(),
        name: this.newName(),
        goal: this.newGoal() || undefined,
        startDate: this.newStart() || undefined,
        endDate: this.newEnd() || undefined,
      });
      this.newName.set('');
      this.newGoal.set('');
      this.toast('Sprint criada.');
    });
  }

  protected async start(sprint: Sprint): Promise<void> {
    await this.run(async () => {
      await this.work.startSprint(sprint.id);
      this.toast(`${sprint.name} iniciada.`);
    });
  }

  /** Antes de encerrar, mostra quantos itens não concluídos voltarão ao backlog (UX/UI 7.7). */
  protected async askClose(sprint: Sprint): Promise<void> {
    const items = await this.work.sprintItems(sprint.id);
    this.closing.set({ sprint, returning: items.filter((i) => i.status.category !== 'DONE').length });
  }

  protected async confirmClose(): Promise<void> {
    const pending = this.closing();
    if (!pending) {
      return;
    }
    await this.run(async () => {
      const result = await this.work.closeSprint(pending.sprint.id);
      this.closing.set(null);
      this.toast(`${result.sprint.name} encerrada; ${result.returnedToBacklog} item(ns) voltaram ao backlog.`);
    });
  }

  protected async remove(sprint: Sprint, item: Artifact): Promise<void> {
    await this.run(async () => {
      await this.work.removeFromSprint(sprint.id, item.id);
      this.items.set(await this.work.sprintItems(sprint.id));
      this.toast(`${item.key} retirado da sprint.`);
    });
  }

  protected stateLabel(state: Sprint['state']): string {
    return { PLANNED: 'Planejada', ACTIVE: 'Ativa', CLOSED: 'Encerrada' }[state];
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await action();
      this.sprints.set(await this.work.sprints(this.squadId()));
    } catch (error) {
      this.error.set(toApiError(error));
    } finally {
      this.busy.set(false);
    }
  }

  private toast(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000, horizontalPosition: 'end', verticalPosition: 'bottom' });
  }
}
