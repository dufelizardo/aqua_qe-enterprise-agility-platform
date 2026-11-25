import { Injectable } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';

import { Block } from './knowledge.service';

/**
 * Rascunho automático (#82): enquanto a pessoa edita uma página do Wiki ou um documento do artefato, o texto fica
 * guardado neste navegador. Ao editar de novo, o rascunho volta; salvar a versão ou descartar apaga o rascunho. Não
 * vai para o servidor: a versão continua sendo criada só pelo Salvar.
 */
export interface StoredDraft {
  /** Versão que estava na tela quando a edição começou. */
  baseVersion: number;
  title?: string;
  sections: { key: string; blocks: Block[] }[];
  /** ISO 8601. */
  savedAt: string;
}

/** O rascunho guardado serve para a versão atual ('same'), é de uma versão anterior ('older') ou não existe. */
export type DraftMatch = 'same' | 'older' | null;

export function draftKey(kind: 'page' | 'doc', id: string): string {
  return `aquaqe.draft.${kind}.${id}`;
}

export function draftMatch(stored: StoredDraft | null, currentVersion: number): DraftMatch {
  if (!stored) {
    return null;
  }
  return stored.baseVersion === currentVersion ? 'same' : 'older';
}

/** Blocos do rascunho por seção, para repor no editor só as seções que ainda existem. */
export function draftBlocks(stored: StoredDraft, key: string): Block[] | undefined {
  return stored.sections.find((s) => s.key === key)?.blocks;
}

export const LEAVE_MESSAGE =
  'Há alterações não salvas. Elas ficam guardadas como rascunho neste navegador e voltam quando você editar de novo. Sair mesmo assim?';

@Injectable({ providedIn: 'root' })
export class DraftStore {
  read(key: string): StoredDraft | null {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as StoredDraft;
      return typeof parsed?.baseVersion === 'number' && Array.isArray(parsed.sections) ? parsed : null;
    } catch {
      return null;
    }
  }

  write(key: string, draft: StoredDraft): boolean {
    try {
      localStorage.setItem(key, JSON.stringify(draft));
      return true;
    } catch {
      return false;
    }
  }

  clear(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      // Sem armazenamento no navegador: não há o que apagar.
    }
  }
}

/** Tela que pode ter alterações não salvas. */
export interface LeaveAware {
  canLeave(): boolean;
}

/** Pergunta antes de sair da rota com alterações não salvas (#82). */
export const confirmLeave: CanDeactivateFn<LeaveAware> = (component) =>
  !component || component.canLeave() || window.confirm(LEAVE_MESSAGE);
