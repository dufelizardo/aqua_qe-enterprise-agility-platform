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
${WIKI_ADD_PARAGRAPH}       css=[data-testid="add-paragraph"]
${WIKI_LAST_BLOCK_INPUT}    css=[data-testid="block-input"] >> nth=-1
${WIKI_SUMMARY}             css=[data-testid="wiki-summary"]
${WIKI_SAVE}                css=[data-testid="wiki-save"]
${WIKI_HISTORY}             css=[data-testid="wiki-history"]
${WIKI_VERSION_LINKS}       css=[data-testid="wiki-version-link"]
${WIKI_SUBMIT_REVIEW}       css=[data-testid="wiki-submit-review"]
${WIKI_APPROVE}             css=[data-testid="wiki-approve"]
${WIKI_BACK_DRAFT}          css=[data-testid="wiki-back-draft"]
${WIKI_ERROR}               css=[data-testid="wiki-error"]
${WIKI_MISSING}             css=[data-testid="wiki-missing"]
# Página da árvore pelo título exato: format com o título.
${WIKI_PAGE_LINK_BY_TITLE}  css=[data-testid="wiki-page-link"]:has(.title:text-is("{}"))
