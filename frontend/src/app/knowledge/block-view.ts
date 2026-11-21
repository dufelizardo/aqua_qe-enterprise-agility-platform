import { Component, input } from '@angular/core';

import { Block, Inline, parseInline } from './knowledge.service';

/** Texto com Markdown leve, montado em elementos (nunca innerHTML). */
@Component({
  selector: 'app-inline-text',
  template: `@for (part of parts(); track $index) {
    @switch (part.kind) {
      @case ('strong') {<strong>{{ part.text }}</strong>}
      @case ('em') {<em>{{ part.text }}</em>}
      @case ('code') {<code>{{ part.text }}</code>}
      @case ('link') {<a [href]="part.href" rel="noopener noreferrer" target="_blank">{{ part.text }}</a>}
      @default {<ng-container>{{ part.text }}</ng-container>}
    }
  }`,
})
export class InlineText {
  readonly text = input('');

  protected parts(): Inline[] {
    return parseInline(this.text());
  }
}

/** Leitura dos blocos de uma seção (SSD 6). */
@Component({
  selector: 'app-block-view',
  imports: [InlineText],
  template: `
    @for (block of blocks(); track $index) {
      @switch (block.type) {
        @case ('heading') {
          @if (block.level === 3) {
            <h4><app-inline-text [text]="block.text" /></h4>
          } @else {
            <h3><app-inline-text [text]="block.text" /></h3>
          }
        }
        @case ('list') {
          @if (block.ordered) {
            <ol>
              @for (item of block.items; track $index) {
                <li><app-inline-text [text]="item" /></li>
              }
            </ol>
          } @else {
            <ul>
              @for (item of block.items; track $index) {
                <li><app-inline-text [text]="item" /></li>
              }
            </ul>
          }
        }
        @case ('table') {
          <div class="table-wrap">
            <table>
              @for (row of block.rows; track $index; let first = $first) {
                <tr>
                  @for (cell of row; track $index) {
                    @if (first) {
                      <th><app-inline-text [text]="cell.trim()" /></th>
                    } @else {
                      <td><app-inline-text [text]="cell.trim()" /></td>
                    }
                  }
                </tr>
              }
            </table>
          </div>
        }
        @case ('code') {
          <pre><code>{{ block.text }}</code></pre>
        }
        @case ('callout') {
          <aside class="callout" [class.warning]="block.tone === 'warning'"><app-inline-text [text]="block.text" /></aside>
        }
        @case ('quote') {
          <blockquote><app-inline-text [text]="block.text" /></blockquote>
        }
        @default {
          <p><app-inline-text [text]="$any(block).text" /></p>
        }
      }
    } @empty {
      <p class="empty">Sem conteúdo.</p>
    }
  `,
  styles: `
    :host { display: block; }
    p, li { margin: 0 0 8px; line-height: 1.6; }
    h3 { margin: 16px 0 8px; font-size: 18px; }
    h4 { margin: 14px 0 6px; font-size: 15px; }
    ul, ol { margin: 0 0 8px; padding-left: 22px; }
    .table-wrap { overflow-x: auto; margin: 0 0 12px; }
    table { border-collapse: collapse; min-width: 50%; }
    th, td { padding: 6px 10px; border: 1px solid var(--border-strong); text-align: left; vertical-align: top; }
    th { background: var(--bg); font-weight: 700; }
    pre { margin: 0 0 12px; padding: 12px; overflow-x: auto; border-radius: 8px; background: var(--navy-900); color: #e6edf3; font-family: var(--font-mono); font-size: 13px; }
    code { font-family: var(--font-mono); font-size: 0.92em; }
    .callout { margin: 0 0 12px; padding: 10px 14px; border-left: 4px solid var(--blue-500); border-radius: 6px; background: var(--blue-50); }
    .callout.warning { border-left-color: var(--amber-700); background: #fff7e6; }
    blockquote { margin: 0 0 12px; padding: 4px 14px; border-left: 3px solid var(--border-strong); color: var(--text-secondary); }
    .empty { color: var(--text-muted); font-style: italic; }
  `,
})
export class BlockView {
  readonly blocks = input<Block[]>([]);
}
