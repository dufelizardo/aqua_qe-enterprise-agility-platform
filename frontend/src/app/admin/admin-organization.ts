import { Component, WritableSignal, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { ApiError, fieldError, messageOf, toApiError } from '../core/api-error';
import {
  OrgLevel,
  OrgUnit,
  OrgUnitDetail,
  OrganizationService,
  buildTree,
  linkCandidates,
} from './organization.service';

/**
 * Administração › Estrutura organizacional (UX/UI, seção 7.10): níveis da instalação (só leitura), árvore das unidades
 * e, para a unidade escolhida, renomear, desativar, criar unidade abaixo e vínculos "atende a".
 */
@Component({
  selector: 'app-admin-organization',
  imports: [MatSnackBarModule, RouterLink],
  templateUrl: './admin-organization.html',
  styleUrl: './admin-organization.scss',
})
export class AdminOrganization {
  private readonly api = inject(OrganizationService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly levels = signal<OrgLevel[]>([]);
  protected readonly units = signal<OrgUnit[]>([]);
  protected readonly rows = computed(() => buildTree(this.units()));
  protected readonly onlyRoot = computed(() => this.units().length === 1);

  protected readonly selectedId = signal<string | null>(null);
  protected readonly detail = signal<OrgUnitDetail | null>(null);
  protected readonly busy = signal(false);

  // Renomear
  protected readonly renameName = signal('');
  protected readonly renameError = signal<ApiError | null>(null);
  protected readonly conflict = signal(false);

  // Nova unidade abaixo da escolhida
  protected readonly newName = signal('');
  protected readonly newLevel = signal('');
  protected readonly createError = signal<ApiError | null>(null);
  protected readonly childLevels = computed(() => {
    const unit = this.detail();
    return unit ? this.levels().filter((level) => level.position > unit.level.position) : [];
  });

  // Vínculos
  protected readonly linkTarget = signal('');
  protected readonly linkError = signal<ApiError | null>(null);
  protected readonly candidates = computed(() => {
    const unit = this.detail();
    return unit ? linkCandidates(unit, this.units()) : [];
  });

  protected readonly fieldError = fieldError;
  protected readonly messageOf = messageOf;

  constructor() {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.loadError.set(null);
    try {
      const [levels, units] = await Promise.all([this.api.levels(), this.api.allUnits()]);
      this.levels.set(levels);
      this.units.set(units);
      const selected = this.selectedId() ?? units.find((u) => u.parentId === null)?.id ?? null;
      if (selected) {
        await this.select(selected);
      }
    } catch (error) {
      this.loadError.set(messageOf(toApiError(error)));
    } finally {
      this.loading.set(false);
    }
  }

  protected async select(id: string): Promise<void> {
    this.selectedId.set(id);
    const detail = await this.api.unit(id);
    this.showDetail(detail);
    this.newName.set('');
    this.newLevel.set(this.childLevels()[0]?.key ?? '');
    this.createError.set(null);
    this.linkTarget.set('');
    this.linkError.set(null);
  }

  /** Escolha feita na árvore; no celular, onde o detalhe fica abaixo da árvore, rola até ele. */
  protected async pick(id: string): Promise<void> {
    // Clicar na unidade já aberta não recarrega: não apaga o que o usuário está digitando nos formulários.
    if (this.detail()?.id !== id) {
      await this.select(id);
    }
    if (window.matchMedia('(max-width: 860px)').matches) {
      document.querySelector('[data-testid="org-detail"]')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  /** Recarrega a unidade depois de um conflito de versão, mantendo o nome digitado (UX/UI, seção 9). */
  protected async reloadKeepingInput(): Promise<void> {
    const typed = this.renameName();
    const id = this.selectedId();
    if (!id) {
      return;
    }
    this.showDetail(await this.api.unit(id));
    this.renameName.set(typed);
  }

  protected async rename(): Promise<void> {
    const unit = this.detail();
    if (!unit) {
      return;
    }
    await this.run(this.renameError, async () => {
      const updated = await this.api.update(unit.id, { name: this.renameName(), version: unit.version });
      await this.afterChange(updated, 'Unidade renomeada.');
    });
  }

  protected async toggleActive(): Promise<void> {
    const unit = this.detail();
    if (!unit) {
      return;
    }
    await this.run(this.renameError, async () => {
      const updated = await this.api.update(unit.id, { active: !unit.active, version: unit.version });
      await this.afterChange(updated, updated.active ? 'Unidade reativada.' : 'Unidade desativada.');
    });
  }

  protected async createChild(): Promise<void> {
    const unit = this.detail();
    if (!unit) {
      return;
    }
    await this.run(this.createError, async () => {
      const created = await this.api.create(this.newName(), this.newLevel(), unit.id);
      this.units.set(await this.api.allUnits());
      await this.select(created.id);
      this.toast('Unidade criada.');
    });
  }

  protected async addLink(): Promise<void> {
    const unit = this.detail();
    const target = this.linkTarget();
    if (!unit || !target) {
      return;
    }
    await this.run(this.linkError, async () => {
      await this.api.link(unit.id, target);
      await this.select(unit.id);
      this.toast('Vínculo criado.');
    });
  }

  protected async removeLink(targetId: string): Promise<void> {
    const unit = this.detail();
    if (!unit) {
      return;
    }
    await this.run(this.linkError, async () => {
      await this.api.unlink(unit.id, targetId);
      await this.select(unit.id);
      this.toast('Vínculo removido.');
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  private showDetail(detail: OrgUnitDetail): void {
    this.detail.set(detail);
    this.renameName.set(detail.name);
    this.renameError.set(null);
    this.conflict.set(false);
  }

  private async afterChange(updated: OrgUnitDetail, message: string): Promise<void> {
    this.showDetail(updated);
    this.units.set(await this.api.allUnits());
    this.toast(message);
  }

  /** Executa uma ação de escrita e mostra o erro no lugar certo (UX/UI, seções 6.10 e 9). */
  private async run(target: WritableSignal<ApiError | null>, action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    target.set(null);
    try {
      await action();
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.code === 'VERSION_CONFLICT') {
        this.conflict.set(true);
      } else if (apiError.code === 'VALIDATION_FAILED') {
        target.set(apiError);
      } else {
        this.toast(messageOf(apiError));
      }
    } finally {
      this.busy.set(false);
    }
  }

  private toast(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000, horizontalPosition: 'end', verticalPosition: 'bottom' });
  }
}
