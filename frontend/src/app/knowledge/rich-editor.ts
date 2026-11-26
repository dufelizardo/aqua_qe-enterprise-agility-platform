import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewEncapsulation,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Editor } from '@tiptap/core';
import { Highlight } from '@tiptap/extension-highlight';
import { TaskItem, TaskList } from '@tiptap/extension-list';
import { TableKit } from '@tiptap/extension-table';
import { Color, TextStyle } from '@tiptap/extension-text-style';
import StarterKit from '@tiptap/starter-kit';

import { EMOJIS, HIGHLIGHTS, TEXT_COLORS } from './editor-palettes';

import { RichDoc, emptyDoc } from './knowledge.service';

type Style = 'paragraph' | 'h2' | 'h3';

/**
 * Editor de texto contínuo da Wiki e do Regimento (bug #67), com Tiptap (ProseMirror): escreve-se direto na área, e a
 * formatação vem da barra e dos atalhos (Ctrl+B, Ctrl+I, Ctrl+U, Ctrl+Z…). O mesmo componente, sem a barra, mostra o
 * conteúdo na leitura. O esquema do ProseMirror só aceita os nós e marcas conhecidos, e o Link recusa endereços
 * perigosos (javascript:), então o conteúdo nunca vira HTML arbitrário.
 */
@Component({
  selector: 'app-rich-editor',
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="rich-editor" [class.editing]="editable()">
      @if (editable()) {
        <div class="rt-toolbar" role="toolbar" [attr.aria-label]="'Formatação de ' + label()" (mousedown)="keepFocus($event)" data-testid="rt-toolbar">
          <select aria-label="Estilo do texto" [value]="style()" (change)="setStyle($any($event.target).value)" data-testid="rt-style">
            <option value="paragraph">Texto</option>
            <option value="h2">Título</option>
            <option value="h3">Subtítulo</option>
          </select>
          <span class="sep" aria-hidden="true"></span>
          <button type="button" [class.on]="is('bold')" (click)="run('toggleBold')" title="Negrito (Ctrl+B)" aria-label="Negrito" data-testid="rt-bold"><b>N</b></button>
          <button type="button" [class.on]="is('italic')" (click)="run('toggleItalic')" title="Itálico (Ctrl+I)" aria-label="Itálico" data-testid="rt-italic"><i>I</i></button>
          <button type="button" [class.on]="is('underline')" (click)="run('toggleUnderline')" title="Sublinhado (Ctrl+U)" aria-label="Sublinhado" data-testid="rt-underline"><u>S</u></button>
          <button type="button" [class.on]="is('code')" (click)="run('toggleCode')" title="Código no texto" aria-label="Código no texto" data-testid="rt-code">&lt;/&gt;</button>
          <button type="button" [class.on]="is('link')" (click)="toggleLink()" title="Link (Ctrl+K)" aria-label="Link" data-testid="rt-link">🔗</button>
          <button type="button" [class.on]="palette() === 'color'" (click)="togglePalette('color')" title="Cor do texto" aria-label="Cor do texto" [attr.aria-expanded]="palette() === 'color'" data-testid="rt-color"><span class="rt-a">A</span></button>
          <button type="button" [class.on]="palette() === 'highlight'" (click)="togglePalette('highlight')" title="Destaque" aria-label="Destaque" [attr.aria-expanded]="palette() === 'highlight'" data-testid="rt-highlight"><span class="rt-hl">ab</span></button>
          <button type="button" [class.on]="palette() === 'emoji'" (click)="togglePalette('emoji')" title="Emoji" aria-label="Inserir emoji" [attr.aria-expanded]="palette() === 'emoji'" data-testid="rt-emoji">😊</button>
          <span class="sep" aria-hidden="true"></span>
          <button type="button" [class.on]="is('bulletList')" (click)="run('toggleBulletList')" title="Lista com marcadores" aria-label="Lista com marcadores" data-testid="rt-bullet">• Lista</button>
          <button type="button" [class.on]="is('orderedList')" (click)="run('toggleOrderedList')" title="Lista numerada" aria-label="Lista numerada" data-testid="rt-ordered">1. Lista</button>
          <button type="button" [class.on]="is('taskList')" (click)="run('toggleTaskList')" title="Lista de tarefas" aria-label="Lista de tarefas" data-testid="rt-task">☑ Tarefas</button>
          <button type="button" [class.on]="is('blockquote')" (click)="run('toggleBlockquote')" title="Citação" aria-label="Citação" data-testid="rt-quote">❝</button>
          <button type="button" [class.on]="is('codeBlock')" (click)="run('toggleCodeBlock')" title="Bloco de código" aria-label="Bloco de código" data-testid="rt-codeblock">{{ '{ }' }}</button>
          <span class="sep" aria-hidden="true"></span>
          @if (is('table')) {
            <button type="button" (click)="run('addRowAfter')" title="Inserir linha abaixo" data-testid="rt-row-add">+ Linha</button>
            <button type="button" (click)="run('addColumnAfter')" title="Inserir coluna à direita" data-testid="rt-col-add">+ Coluna</button>
            <button type="button" (click)="run('deleteRow')" title="Remover linha">− Linha</button>
            <button type="button" (click)="run('deleteColumn')" title="Remover coluna">− Coluna</button>
            <button type="button" (click)="run('deleteTable')" title="Remover tabela">Remover tabela</button>
          } @else {
            <button type="button" (click)="insertTable()" title="Inserir tabela" aria-label="Inserir tabela" data-testid="rt-table">▦ Tabela</button>
          }
          <span class="sep" aria-hidden="true"></span>
          <button type="button" (click)="run('undo')" title="Desfazer (Ctrl+Z)" aria-label="Desfazer">↶</button>
          <button type="button" (click)="run('redo')" title="Refazer (Ctrl+Shift+Z)" aria-label="Refazer">↷</button>
        </div>
        @if (palette(); as open) {
          <div class="rt-palette" role="group" [attr.aria-label]="open === 'color' ? 'Cores do texto' : open === 'highlight' ? 'Cores de destaque' : 'Emojis'" (mousedown)="keepFocus($event)" data-testid="rt-palette">
            @if (open === 'color') {
              @for (s of textColors; track s.color) {
                <button type="button" class="swatch" [style.color]="s.color" (click)="setColor(s.color)" [title]="s.label" [attr.aria-label]="s.label" data-testid="rt-color-option">A</button>
              }
              <button type="button" (click)="setColor(null)" data-testid="rt-color-clear">Sem cor</button>
            } @else if (open === 'highlight') {
              @for (s of highlights; track s.color) {
                <button type="button" class="swatch" [style.background]="s.color" (click)="setHighlight(s.color)" [title]="s.label" [attr.aria-label]="s.label" data-testid="rt-highlight-option">ab</button>
              }
              <button type="button" (click)="setHighlight(null)" data-testid="rt-highlight-clear">Sem destaque</button>
            } @else {
              @for (e of emojis; track e) {
                <button type="button" class="emoji" (click)="insertEmoji(e)" [attr.aria-label]="'Inserir ' + e" data-testid="rt-emoji-option">{{ e }}</button>
              }
            }
          </div>
        }
        @if (linkOpen()) {
          <form class="rt-link" (submit)="applyLink($event)" data-testid="rt-link-form">
            <input
              type="url"
              placeholder="https://…"
              aria-label="Endereço do link"
              [value]="linkUrl()"
              (input)="linkUrl.set($any($event.target).value)"
              data-testid="rt-link-url"
            />
            <button type="submit">Aplicar</button>
            @if (is('link')) {
              <button type="button" (click)="removeLink()">Remover link</button>
            }
            <button type="button" (click)="linkOpen.set(false)">Cancelar</button>
          </form>
        }
      }
      <div #host class="rt-host"></div>
    </div>
  `,
  styles: `
    .rich-editor { display: block; }
    .rich-editor.editing { border: 1px solid var(--border-strong); border-radius: 10px; background: var(--surface); }
    .rt-toolbar { position: sticky; top: 0; z-index: 1; display: flex; flex-wrap: wrap; align-items: center; gap: 4px; padding: 6px; border-bottom: 1px solid var(--border); border-radius: 10px 10px 0 0; background: var(--bg); }
    .rt-toolbar button, .rt-link button { min-height: 30px; min-width: 30px; padding: 0 8px; border: 1px solid transparent; border-radius: 6px; background: none; color: var(--text); font: inherit; font-size: 13px; cursor: pointer; }
    .rt-toolbar button:hover { background: var(--surface); border-color: var(--border-strong); }
    .rt-toolbar button.on { background: var(--navy-900); color: #fff; }
    .rt-toolbar select { min-height: 30px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; font-size: 13px; }
    .rt-toolbar .sep { width: 1px; height: 20px; margin: 0 2px; background: var(--border-strong); }
    .rt-link { display: flex; flex-wrap: wrap; gap: 6px; padding: 6px; border-bottom: 1px solid var(--border); }
    .rt-link input { flex: 1 1 220px; min-height: 30px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; font: inherit; }
    .rt-link button { border-color: var(--border-strong); }
    .rich-editor.editing .rich-content { min-height: 140px; padding: 12px 16px; }
    .rich-content { outline: none; line-height: 1.6; overflow-wrap: anywhere; }
    .rich-content:focus-visible { box-shadow: inset 0 0 0 2px var(--blue-500); border-radius: 0 0 10px 10px; }
    .rich-content > :first-child { margin-top: 0; }
    .rich-content p { margin: 0 0 8px; }
    .rich-content h2 { margin: 16px 0 8px; font-size: 20px; }
    .rich-content h3 { margin: 14px 0 6px; font-size: 16px; }
    .rich-content ul, .rich-content ol { margin: 0 0 8px; padding-left: 22px; }
    .rich-content blockquote { margin: 0 0 12px; padding: 4px 14px; border-left: 3px solid var(--border-strong); color: var(--text-secondary); }
    .rich-content code { padding: 1px 4px; border-radius: 4px; background: var(--bg); font-family: var(--font-mono); font-size: 0.92em; }
    .rich-content pre { margin: 0 0 12px; padding: 12px; overflow-x: auto; border-radius: 8px; background: var(--navy-900); color: #e6edf3; font-family: var(--font-mono); font-size: 13px; }
    .rich-content pre code { padding: 0; background: none; color: inherit; }
    .rich-content a { color: var(--blue-700); text-decoration: underline; }
    .rich-content table { border-collapse: collapse; margin: 0 0 12px; min-width: 50%; table-layout: fixed; }
    .rich-content th, .rich-content td { position: relative; padding: 6px 10px; border: 1px solid var(--border-strong); vertical-align: top; text-align: left; }
    .rich-content th { background: var(--bg); font-weight: 700; }
    .rich-content th p, .rich-content td p { margin: 0; }
    .rich-content .selectedCell::after { position: absolute; inset: 0; background: rgba(30, 155, 215, 0.15); content: ''; pointer-events: none; }
    .rich-content .tableWrapper { overflow-x: auto; }
    .rt-palette { display: flex; flex-wrap: wrap; gap: 4px; padding: 6px; border-bottom: 1px solid var(--border); }
    .rt-palette button { min-height: 30px; min-width: 30px; padding: 0 8px; border: 1px solid var(--border-strong); border-radius: 6px; background: var(--surface); color: var(--text); font: inherit; font-size: 13px; cursor: pointer; }
    .rt-palette .swatch { font-weight: 700; color: #1f2937; }
    .rt-palette .emoji { border-color: transparent; font-size: 18px; padding: 0 4px; }
    .rt-a { font-weight: 800; text-decoration: underline; text-decoration-color: #b42318; text-decoration-thickness: 3px; }
    .rt-hl { padding: 0 3px; border-radius: 3px; background: #fef08a; color: #1f2937; }
    .rich-content mark { padding: 0 2px; border-radius: 3px; color: #1f2937; }
    .rich-content ul[data-type="taskList"] { padding-left: 2px; list-style: none; }
    .rich-content ul[data-type="taskList"] li { display: flex; align-items: flex-start; gap: 8px; }
    .rich-content ul[data-type="taskList"] li > label { flex: none; margin-top: 3px; }
    .rich-content ul[data-type="taskList"] li > div { flex: 1 1 auto; min-width: 0; }
    .rich-content ul[data-type="taskList"] li[data-checked="true"] > div { color: var(--text-secondary); text-decoration: line-through; }
    .rich-content ul[data-type="taskList"] input[type="checkbox"] { width: 16px; height: 16px; accent-color: var(--navy-900); }
    .rich-content p.is-editor-empty:first-child::before { float: left; height: 0; color: var(--text-muted); content: attr(data-placeholder); pointer-events: none; }
  `,
})
export class RichEditor implements AfterViewInit, OnDestroy {
  readonly doc = input<RichDoc>(emptyDoc());
  readonly editable = input(false);
  readonly label = input('Conteúdo');
  readonly docChange = output<RichDoc>();

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private editor: Editor | null = null;
  /** Muda a cada transação, para a barra recalcular o que está ativo. */
  private readonly tick = signal(0);
  protected readonly linkOpen = signal(false);
  /** Paleta aberta na barra: cor do texto, destaque ou emoji (#86). */
  protected readonly palette = signal<'color' | 'highlight' | 'emoji' | null>(null);
  protected readonly textColors = TEXT_COLORS;
  protected readonly highlights = HIGHLIGHTS;
  protected readonly emojis = EMOJIS;
  protected readonly linkUrl = signal('');

  constructor() {
    // Conteúdo trocado por fora (outra versão, recarga depois de conflito): atualiza sem disparar docChange.
    effect(() => {
      const doc = this.doc();
      const editor = this.editor;
      if (editor && JSON.stringify(editor.getJSON()) !== JSON.stringify(doc)) {
        editor.commands.setContent(doc, { emitUpdate: false });
      }
    });
    effect(() => this.editor?.setEditable(this.editable(), false));
  }

  /**
   * Botões da barra não tiram o foco do texto: sem isso, o foco volta ao editor só no quadro seguinte, e o que se
   * digita logo depois do clique se perde. A lista de estilo e o campo do link continuam recebendo o foco.
   */
  protected keepFocus(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('select, input')) {
      event.preventDefault();
    }
  }

  ngAfterViewInit(): void {
    this.editor = new Editor({
      element: this.host().nativeElement,
      editable: this.editable(),
      content: this.doc(),
      extensions: [
        StarterKit.configure({
          heading: { levels: [2, 3] },
          link: {
            openOnClick: false,
            autolink: true,
            defaultProtocol: 'https',
            HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' },
          },
        }),
        TableKit.configure({ table: { resizable: false } }),
        TaskList,
        TaskItem.configure({ nested: true }),
        TextStyle,
        Color,
        Highlight.configure({ multicolor: true }),
      ],
      editorProps: {
        attributes: {
          class: 'rich-content',
          'data-testid': 'rich-content',
          'aria-label': this.label(),
          'aria-multiline': 'true',
          role: 'textbox',
        },
        handleKeyDown: (_view, event) => {
          if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && this.editable()) {
            event.preventDefault();
            this.toggleLink();
            return true;
          }
          return false;
        },
      },
      onTransaction: () => this.tick.update((n) => n + 1),
      onUpdate: ({ editor }) => this.docChange.emit(editor.getJSON() as RichDoc),
    });
    this.tick.update((n) => n + 1);
  }

  ngOnDestroy(): void {
    this.editor?.destroy();
    this.editor = null;
  }

  protected is(name: string): boolean {
    this.tick();
    return this.editor?.isActive(name) ?? false;
  }

  protected style(): Style {
    this.tick();
    if (this.editor?.isActive('heading', { level: 2 })) {
      return 'h2';
    }
    return this.editor?.isActive('heading', { level: 3 }) ? 'h3' : 'paragraph';
  }

  protected setStyle(style: Style): void {
    const chain = this.editor?.chain().focus();
    if (!chain) {
      return;
    }
    (style === 'paragraph' ? chain.setParagraph() : chain.setHeading({ level: style === 'h2' ? 2 : 3 })).run();
  }

  /** Executa um comando do editor pelo nome, devolvendo o foco ao texto. */
  protected run(command: string): void {
    const chain = this.editor?.chain().focus() as unknown as Record<string, () => { run: () => boolean }> | undefined;
    chain?.[command]?.().run();
  }

  protected insertTable(): void {
    this.editor?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
  }

  protected togglePalette(name: 'color' | 'highlight' | 'emoji'): void {
    this.palette.set(this.palette() === name ? null : name);
    this.linkOpen.set(false);
  }

  /** Cor do texto selecionado; nulo tira a cor. */
  protected setColor(color: string | null): void {
    const chain = this.editor?.chain().focus();
    (color ? chain?.setColor(color) : chain?.unsetColor())?.run();
    this.palette.set(null);
  }

  /** Destaque do texto selecionado; nulo tira o destaque. */
  protected setHighlight(color: string | null): void {
    const chain = this.editor?.chain().focus();
    (color ? chain?.setHighlight({ color }) : chain?.unsetHighlight())?.run();
    this.palette.set(null);
  }

  protected insertEmoji(emoji: string): void {
    this.editor?.chain().focus().insertContent(emoji).run();
    this.palette.set(null);
  }

  protected toggleLink(): void {
    const current = (this.editor?.getAttributes('link')['href'] as string | undefined) ?? '';
    this.linkUrl.set(current);
    this.palette.set(null);
    this.linkOpen.set(!this.linkOpen());
  }

  protected applyLink(event: Event): void {
    event.preventDefault();
    const href = this.linkUrl().trim();
    if (!href) {
      this.removeLink();
      return;
    }
    this.editor?.chain().focus().extendMarkRange('link').setLink({ href }).run();
    this.linkOpen.set(false);
  }

  protected removeLink(): void {
    this.editor?.chain().focus().extendMarkRange('link').unsetLink().run();
    this.linkOpen.set(false);
  }
}
