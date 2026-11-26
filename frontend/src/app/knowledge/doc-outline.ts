import { Component, computed, input } from '@angular/core';

import { RichNode } from './knowledge.service';

/** Título do documento para o sumário: nível (2 ou 3) e texto. */
export interface Heading {
  level: number;
  text: string;
}

/** Texto de um nó, juntando os filhos. */
function nodeText(node: RichNode): string {
  return node.text ?? (node.content ?? []).map(nodeText).join('');
}

/** Títulos e subtítulos dos documentos, na ordem em que aparecem (#86). Títulos vazios ficam de fora. */
export function docHeadings(docs: RichNode[]): Heading[] {
  const result: Heading[] = [];
  const walk = (node: RichNode) => {
    if (node.type === 'heading') {
      const text = nodeText(node).trim();
      if (text) {
        result.push({ level: Number(node.attrs?.['level'] ?? 2), text });
      }
      return;
    }
    (node.content ?? []).forEach(walk);
  };
  docs.forEach(walk);
  return result;
}

/**
 * Sumário automático (#86): os títulos e subtítulos do conteúdo, com link para cada um. Aparece com dois títulos ou
 * mais. O clique leva ao título correspondente dentro de `container` (os h2 e h3 do editor, na mesma ordem).
 */
@Component({
  selector: 'app-doc-outline',
  template: `
    @if (headings().length >= 2) {
      <nav class="outline" aria-label="Sumário" data-testid="doc-outline">
        <strong>Sumário</strong>
        <ol>
          @for (h of headings(); track $index) {
            <li [class.sub]="h.level > 2">
              <button type="button" class="link" (click)="go($index)" data-testid="doc-outline-item">{{ h.text }}</button>
            </li>
          }
        </ol>
      </nav>
    }
  `,
  styles: `
    .outline { margin: 0 0 16px; padding: 10px 14px; border: 1px solid var(--border); border-radius: 10px; background: var(--bg); }
    strong { display: block; margin-bottom: 4px; font-size: 13px; color: var(--text-secondary); }
    ol { margin: 0; padding-left: 18px; }
    li.sub { margin-left: 16px; list-style: circle; }
    .link { border: none; background: none; padding: 2px 0; color: var(--blue-700, inherit); cursor: pointer; font: inherit; font-size: 14px; text-align: left; }
    .link:hover { text-decoration: underline; }
  `,
})
export class DocOutline {
  readonly docs = input<RichNode[]>([]);
  readonly container = input<HTMLElement | null>(null);
  protected readonly headings = computed(() => docHeadings(this.docs()));

  protected go(index: number): void {
    const titles = this.container()?.querySelectorAll<HTMLElement>('.rich-content h2, .rich-content h3');
    const target = titles?.[index];
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      target.setAttribute('tabindex', '-1');
      target.focus({ preventScroll: true });
    }
  }
}
