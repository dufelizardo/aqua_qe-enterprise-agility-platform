import { Component, model } from '@angular/core';

import { Block, BlockType, emptyBlock } from './knowledge.service';

const TYPE_LABELS: Record<BlockType, string> = {
  paragraph: 'Parágrafo',
  heading: 'Título',
  list: 'Lista',
  table: 'Tabela',
  code: 'Código',
  callout: 'Destaque',
  quote: 'Citação',
};

/**
 * Editor de blocos de uma seção (REV-05: editor próprio sobre o contrato do SSD 6). Parágrafo, destaque e citação
 * aceitam Markdown leve (**negrito**, *itálico*, `código`, [link](endereço)). Lista: um item por linha. Tabela: uma
 * linha por linha, células separadas por "|"; a primeira linha é o cabeçalho.
 */
@Component({
  selector: 'app-block-editor',
  template: `
    @for (block of blocks(); track $index; let i = $index, first = $first, last = $last) {
      <div class="block" data-testid="block">
        <div class="bar">
          <span class="kind">{{ label(block.type) }}</span>
          @if (block.type === 'heading') {
            <select aria-label="Nível do título" [value]="block.level" (change)="patch(i, { level: +value($event) })">
              <option [value]="2" [selected]="block.level === 2">Título</option>
              <option [value]="3" [selected]="block.level === 3">Subtítulo</option>
            </select>
          }
          @if (block.type === 'list') {
            <label class="check">
              <input type="checkbox" [checked]="block.ordered" (change)="patch(i, { ordered: !block.ordered })" />
              Numerada
            </label>
          }
          @if (block.type === 'callout') {
            <select aria-label="Tom do destaque" (change)="patch(i, { tone: value($event) })">
              <option value="info" [selected]="block.tone !== 'warning'">Informação</option>
              <option value="warning" [selected]="block.tone === 'warning'">Atenção</option>
            </select>
          }
          <span class="spacer"></span>
          <button type="button" (click)="move(i, -1)" [disabled]="first" aria-label="Subir bloco">↑</button>
          <button type="button" (click)="move(i, 1)" [disabled]="last" aria-label="Descer bloco">↓</button>
          <button type="button" (click)="remove(i)" aria-label="Remover bloco" data-testid="block-remove">✕</button>
        </div>
        @switch (block.type) {
          @case ('heading') {
            <input class="field" [value]="block.text" (input)="patch(i, { text: value($event) })" aria-label="Texto do título" data-testid="block-input" />
          }
          @case ('list') {
            <textarea class="field" rows="3" [value]="block.items.join('\n')" (input)="patch(i, { items: lines($event) })" aria-label="Itens da lista, um por linha" data-testid="block-input"></textarea>
          }
          @case ('table') {
            <textarea class="field mono" rows="4" [value]="tableText(block.rows)" (input)="patch(i, { rows: table($event) })" aria-label="Tabela: células separadas por barra vertical" data-testid="block-input"></textarea>
          }
          @case ('code') {
            <textarea class="field mono" rows="5" [value]="block.text" (input)="patch(i, { text: value($event) })" aria-label="Código" data-testid="block-input"></textarea>
          }
          @default {
            <textarea class="field" rows="3" [value]="$any(block).text" (input)="patch(i, { text: value($event) })" [attr.aria-label]="label(block.type)" data-testid="block-input"></textarea>
          }
        }
      </div>
    }
    <div class="add" role="group" aria-label="Adicionar bloco">
      <span>Adicionar:</span>
      @for (type of types; track type) {
        <button type="button" (click)="add(type)" [attr.data-testid]="'add-' + type">{{ label(type) }}</button>
      }
    </div>
  `,
  styles: `
    :host { display: grid; gap: 10px; }
    .block { border: 1px solid var(--border); border-radius: 10px; background: var(--surface); padding: 8px; }
    .bar { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 6px; font-size: 12px; }
    .kind { font-weight: 800; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.06em; }
    .spacer { flex: 1; }
    .bar button, .add button { min-height: 28px; padding: 0 10px; border: 1px solid var(--border-strong); border-radius: 6px; background: var(--surface); font: inherit; cursor: pointer; }
    .bar button:disabled { opacity: 0.4; cursor: default; }
    .bar select { font: inherit; }
    .check { display: inline-flex; align-items: center; gap: 4px; }
    .field { width: 100%; box-sizing: border-box; padding: 8px 10px; border: 1px solid var(--border-strong); border-radius: 8px; font: inherit; resize: vertical; }
    .mono { font-family: var(--font-mono); font-size: 13px; }
    .add { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 13px; color: var(--text-secondary); }
    button:focus-visible, .field:focus-visible { outline: 2px solid var(--blue-500); outline-offset: 1px; }
  `,
})
export class BlockEditor {
  readonly blocks = model<Block[]>([]);
  protected readonly types = Object.keys(TYPE_LABELS) as BlockType[];

  protected label(type: BlockType): string {
    return TYPE_LABELS[type];
  }

  protected add(type: BlockType): void {
    this.blocks.set([...this.blocks(), emptyBlock(type)]);
  }

  protected remove(index: number): void {
    this.blocks.set(this.blocks().filter((_, i) => i !== index));
  }

  protected move(index: number, delta: number): void {
    const next = [...this.blocks()];
    const [block] = next.splice(index, 1);
    next.splice(index + delta, 0, block!);
    this.blocks.set(next);
  }

  protected patch(index: number, changes: Record<string, unknown>): void {
    this.blocks.set(this.blocks().map((b, i) => (i === index ? ({ ...b, ...changes } as Block) : b)));
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected lines(event: Event): string[] {
    return this.value(event).split('\n');
  }

  /** Sem normalizar: o texto volta idêntico ao digitado, e o cursor não pula. A leitura apara as células. */
  protected table(event: Event): string[][] {
    return this.value(event)
      .split('\n')
      .map((line) => line.split('|'));
  }

  protected tableText(rows: string[][]): string {
    return rows.map((row) => row.join('|')).join('\n');
  }
}
