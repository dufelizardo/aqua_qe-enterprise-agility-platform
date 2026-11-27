import { Component, computed, inject, input, output, signal } from '@angular/core';

import { ApiError, messageOf, toApiError } from '../core/api-error';
import { Board, ColumnConfig, WipPolicy, WorkService, configProblems } from './work.service';

/**
 * Configurar quadro (UX/UI 6.2; PRD-RF-011; Proposta 04): nome, colunas (nome, fase, status agrupados, ordem) e limite
 * de WIP com a política de cada coluna. Status sem coluna geram aviso, porque os itens neles ficariam invisíveis.
 */
@Component({
  selector: 'app-board-config',
  templateUrl: './board-config.html',
  styleUrl: './board-config.scss',
})
export class BoardConfig {
  private readonly work = inject(WorkService);

  readonly board = input.required<Board>();
  readonly saved = output<Board>();
  readonly closed = output<void>();

  protected readonly name = signal('');
  protected readonly columns = signal<ColumnConfig[]>([]);
  protected readonly statuses = signal<{ key: string; name: string }[]>([]);
  protected readonly busy = signal(false);
  protected readonly error = signal<ApiError | null>(null);

  protected readonly problems = computed(() => configProblems(this.columns()));
  protected readonly unmapped = computed(() => {
    const used = new Set(this.columns().flatMap((c) => c.statusKeys));
    return this.statuses().filter((s) => s.key !== 'CANCELLED' && !used.has(s.key));
  });
  protected readonly messageOf = messageOf;

  async ngOnInit(): Promise<void> {
    const board = this.board();
    this.name.set(board.name);
    this.columns.set(board.columns.map((c) => ({ ...c, statusKeys: [...c.statusKeys] })));
    this.statuses.set(await this.work.levelStatuses(board.level, board.orgUnitId));
  }

  protected statusName(key: string): string {
    return this.statuses().find((s) => s.key === key)?.name ?? key;
  }

  protected edit(index: number, changes: Partial<ColumnConfig>): void {
    this.columns.update((list) => list.map((c, i) => (i === index ? { ...c, ...changes } : c)));
  }

  protected toggleStatus(index: number, key: string, checked: boolean): void {
    const column = this.columns()[index]!;
    const keys = checked ? [...column.statusKeys, key] : column.statusKeys.filter((k) => k !== key);
    this.edit(index, { statusKeys: keys });
  }

  protected limit(index: number, value: string): void {
    const parsed = Number.parseInt(value, 10);
    this.edit(index, { wipLimit: value.trim() === '' || Number.isNaN(parsed) ? null : parsed });
  }

  protected policy(index: number, value: string): void {
    this.edit(index, { wipPolicy: value as WipPolicy });
  }

  protected move(index: number, delta: number): void {
    const target = index + delta;
    this.columns.update((list) => {
      if (target < 0 || target >= list.length) {
        return list;
      }
      const copy = [...list];
      [copy[index], copy[target]] = [copy[target]!, copy[index]!];
      return copy;
    });
  }

  protected add(): void {
    this.columns.update((list) => [
      ...list,
      { name: 'Nova coluna', phase: 'DOWNSTREAM', statusKeys: [], wipLimit: null, wipPolicy: 'WARN' },
    ]);
  }

  protected remove(index: number): void {
    this.columns.update((list) => list.filter((_, i) => i !== index));
  }

  protected async save(): Promise<void> {
    if (this.problems().length > 0) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      this.saved.emit(await this.work.updateBoard(this.board().id, { name: this.name(), columns: this.columns() }));
    } catch (error) {
      this.error.set(toApiError(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }

  protected checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }
}
