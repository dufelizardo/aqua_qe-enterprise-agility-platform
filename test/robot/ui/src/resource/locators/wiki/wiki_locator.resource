*** Comments ***
    Locators da tela Wiki (UX/UI 7.12; Proposta 07). Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${WIKI_TREE}                css=[data-testid="wiki-tree"]
${WIKI_NEW_PAGE}            css=[data-testid="wiki-new-page"]
${REGIMENTO_CREATE}         css=[data-testid="regimento-create"]
${REGIMENTO_EMPTY}          css=[data-testid="regimento-empty"]
${WIKI_NEW_TITLE}           css=[data-testid="wiki-new-title"]
${WIKI_CREATE_SUBMIT}       css=[data-testid="wiki-create-submit"]
${WIKI_PAGE}                css=[data-testid="wiki-page"]
${WIKI_PAGE_TITLE}          css=[data-testid="wiki-page-title"]
${WIKI_PAGE_VERSION}        css=[data-testid="wiki-page-version"]
${WIKI_PAGE_STATUS}         css=[data-testid="wiki-page-status"]
${WIKI_EDIT}                css=[data-testid="wiki-edit"]
# Editor de texto contínuo (bug #67): a área editável e o botão de negrito da barra.
${WIKI_EDITOR}              css=.rich-editor.editing [data-testid="rich-content"]
${WIKI_RT_BOLD}             css=[data-testid="rt-bold"]
${WIKI_READ_BOLD}           css=[data-testid="wiki-page"] [data-testid="rich-content"] strong
${WIKI_SUMMARY}             css=[data-testid="wiki-summary"]
${WIKI_SAVE}                css=[data-testid="wiki-save"]
${WIKI_HISTORY}             css=[data-testid="wiki-history"]
${WIKI_VERSION_LINKS}       css=[data-testid="wiki-version-link"]
${WIKI_SUBMIT_REVIEW}       css=[data-testid="wiki-submit-review"]
${WIKI_APPROVE}             css=[data-testid="wiki-approve"]
${WIKI_BACK_DRAFT}          css=[data-testid="wiki-back-draft"]
${WIKI_ERROR}               css=[data-testid="wiki-error"]
# Organizar a árvore (PROP-002).
${WIKI_CURRENT_NODE_MENU}   css=[data-testid="wiki-tree"] li.current [data-testid="wiki-node-menu"]
${WIKI_UP}                  css=[data-testid="wiki-up"]
${WIKI_MOVE}                css=[data-testid="wiki-move"]
${WIKI_MOVE_TARGET}         css=[data-testid="wiki-move-target"]
${WIKI_MOVE_CONFIRM}        css=[data-testid="wiki-move-confirm"]
${WIKI_TREE_TITLES}         css=[data-testid="wiki-tree"] [data-testid="wiki-page-link"] .title
# Item da árvore pelo título exato: format com o título.
${WIKI_TREE_ITEM_BY_TITLE}  css=[data-testid="wiki-tree"] li[role="treeitem"]:has(.title:text-is("{}"))
${WIKI_MISSING}             css=[data-testid="wiki-missing"]
# Página da árvore pelo título exato: format com o título.
${WIKI_PAGE_LINK_BY_TITLE}  css=[data-testid="wiki-page-link"]:has(.title:text-is("{}"))
# Menu ⋯ da página: renomear, fazer uma cópia e arquivar.
${WIKI_RENAME}              css=[data-testid="wiki-rename"]
${WIKI_RENAME_TITLE}        css=[data-testid="wiki-rename-title"]
${WIKI_RENAME_CONFIRM}      css=[data-testid="wiki-rename-confirm"]
${WIKI_COPY}                css=[data-testid="wiki-copy"]
${WIKI_MENU_ARCHIVE}        css=[data-testid="wiki-menu-archive"]
${WIKI_ARCHIVE_CONFIRM}     css=[data-testid="wiki-archive-confirm"]
# Rascunho automático (#82).
${WIKI_DRAFT_STATUS}        css=[data-testid="wiki-draft-status"]
${WIKI_DRAFT_RESTORED}      css=[data-testid="wiki-draft-restored"]
# Comentários, @menção e favoritos (#85).
${WIKI_READ_PARAGRAPH}      css=[data-testid="wiki-page"] .reading [data-testid="rich-content"] p >> nth=0
${WIKI_COMMENT_SELECTION}   css=[data-testid="wiki-comment-selection"]
${WIKI_FAVORITE}            css=[data-testid="wiki-favorite"]
${COMMENT_QUOTE_PREVIEW}    css=[data-testid="comment-quote-preview"]
${COMMENT_INPUT}            css=[data-testid="comment-input"]
${COMMENT_MENTION_OPTION}   css=[data-testid="comment-mention-option"] >> nth=0
${COMMENT_SEND}             css=[data-testid="comment-send"]
${COMMENT_ITEM}             css=[data-testid="comment-item"] >> nth=0
# Editor: sumário, tarefas, emoji e ícone (#86).
${RT_STYLE}                 css=.rich-editor.editing [data-testid="rt-style"]
${RT_TASK}                  css=.rich-editor.editing [data-testid="rt-task"]
${RT_EMOJI}                 css=.rich-editor.editing [data-testid="rt-emoji"]
${RT_EMOJI_OPTION}          css=[data-testid="rt-emoji-option"] >> nth=0
${DOC_OUTLINE}              css=[data-testid="doc-outline"]
${DOC_OUTLINE_ITEM}         css=[data-testid="doc-outline-item"]
${WIKI_READ_TASK_ITEM}      css=[data-testid="wiki-page"] .reading ul[data-type="taskList"] li
${WIKI_ICON_BUTTON}         css=[data-testid="wiki-icon-button"]
${WIKI_ICON_OPTION}         css=[data-testid="wiki-icon-option"] >> nth=0
