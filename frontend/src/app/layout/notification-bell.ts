import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { WorkspaceService } from '../core/workspace.service';

/** Aviso de GET /me/notifications (#85). */
export interface AppNotification {
  id: string;
  kind: 'MENTION' | 'REPLY';
  actor: { id: string; name: string | null } | null;
  targetType: 'PAGE';
  targetId: string;
  orgUnitId: string | null;
  title: string | null;
  excerpt: string | null;
  createdAt: string;
  read: boolean;
}

/** Frase do aviso: "Paula mencionou você em Guia" ou "Tiago respondeu ao seu comentário em Guia". */
export function notificationText(n: AppNotification): string {
  const who = n.actor?.name ?? 'Alguém';
  const where = n.title ?? 'uma página';
  return n.kind === 'REPLY' ? `${who} respondeu ao seu comentário em ${where}` : `${who} mencionou você em ${where}`;
}

/** Contador exibido no sino: até 9, depois "9+". */
export function badgeText(unread: number): string {
  return unread > 9 ? '9+' : String(unread);
}

const REFRESH_MS = 60_000;

/**
 * Sino de notificações do cabeçalho (#85): contador de não lidas, atualizado a cada minuto e ao abrir; abrir um aviso
 * marca como lido e leva à página, na unidade dela.
 */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe, MatMenuModule],
  template: `
    <button type="button" class="bell" [matMenuTriggerFor]="menu" (menuOpened)="refresh()"
      [attr.aria-label]="unread() > 0 ? 'Notificações: ' + unread() + ' não lidas' : 'Notificações'" data-testid="notifications-button">
      <span aria-hidden="true">🔔</span>
      @if (unread() > 0) {
        <span class="badge" data-testid="notifications-badge">{{ badge(unread()) }}</span>
      }
    </button>
    <mat-menu #menu="matMenu" xPosition="before" class="notifications-menu">
      <div class="panel">
        <div class="top">
          <strong>Notificações</strong>
          @if (unread() > 0) {
            <button type="button" class="link" (click)="$event.stopPropagation(); readAll()" data-testid="notifications-read-all">Marcar todas como lidas</button>
          }
        </div>
        @for (n of items(); track n.id) {
          <button type="button" class="item" [class.unread]="!n.read" (click)="open(n)" data-testid="notification-item">
            <span class="text">{{ text(n) }}</span>
            @if (n.excerpt) {
              <span class="excerpt">"{{ n.excerpt }}"</span>
            }
            <span class="when">{{ n.createdAt | date: 'dd/MM HH:mm' }}</span>
          </button>
        } @empty {
          <p class="empty" data-testid="notifications-empty">Nenhuma notificação.</p>
        }
      </div>
    </mat-menu>
  `,
  styles: `
    :host { display: inline-flex; }
    .bell { position: relative; border: none; background: none; cursor: pointer; font-size: 18px; padding: 6px 8px; border-radius: 8px; color: inherit; }
    .bell:hover { background: var(--hover, rgba(0, 0, 0, .06)); }
    .badge { position: absolute; top: 0; right: 0; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: #d92d20; color: #fff; font-size: 11px; font-weight: 700; line-height: 16px; text-align: center; box-sizing: border-box; }
    .panel { width: min(360px, calc(100vw - 32px)); padding: 4px 0; }
    .top { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 8px 16px; }
    .link { background: none; border: none; padding: 0; color: var(--primary, inherit); cursor: pointer; font: inherit; font-size: 13px; }
    .item { display: grid; gap: 2px; width: 100%; text-align: left; padding: 10px 16px; border: none; background: none; cursor: pointer; font: inherit; color: inherit; }
    .item:hover { background: var(--hover, rgba(0, 0, 0, .06)); }
    .item.unread .text { font-weight: 700; }
    .item.unread { box-shadow: inset 3px 0 0 var(--primary, #1d4ed8); }
    .excerpt { font-size: 13px; color: var(--text-secondary); overflow-wrap: anywhere; }
    .when { font-size: 12px; color: var(--text-secondary); }
    .empty { margin: 0; padding: 8px 16px 12px; color: var(--text-secondary); font-size: 14px; }
  `,
})
export class NotificationBell {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly workspace = inject(WorkspaceService);

  protected readonly unread = signal(0);
  protected readonly items = signal<AppNotification[]>([]);
  protected readonly text = notificationText;
  protected readonly badge = badgeText;

  constructor() {
    void this.refresh();
    const timer = setInterval(() => void this.refresh(), REFRESH_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  async refresh(): Promise<void> {
    try {
      const inbox = await firstValueFrom(
        this.http.get<{ unread: number; items: AppNotification[] }>('/api/v1/me/notifications'),
      );
      this.unread.set(inbox.unread);
      this.items.set(inbox.items);
    } catch {
      // Sem notificações agora; a próxima rodada tenta de novo.
    }
  }

  protected async open(n: AppNotification): Promise<void> {
    if (!n.read) {
      try {
        await firstValueFrom(this.http.post<void>(`/api/v1/me/notifications/${n.id}/read`, null));
      } catch {
        // Marcar como lido é secundário: a navegação segue.
      }
    }
    if (n.orgUnitId && this.workspace.workspaces().some((w) => w.id === n.orgUnitId)) {
      this.workspace.choose(n.orgUnitId);
    }
    void this.router.navigate(['/wiki'], { queryParams: { page: n.targetId } });
    void this.refresh();
  }

  protected async readAll(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/v1/me/notifications/read-all', null));
    } finally {
      void this.refresh();
    }
  }
}
