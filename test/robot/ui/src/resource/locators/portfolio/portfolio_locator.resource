*** Comments ***
    Locators da tela de Portfolio (UX/UI 7.3). Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${PORTFOLIO_NAV_LINK}               css=[data-testid="nav-portfolio"]
${PORTFOLIO_PAGE_TITLE}             css=[data-testid="page-title"]
${PORTFOLIO_CREATE_BOARD}           css=[data-testid="create-board"]
${PORTFOLIO_BOARD}                  css=[data-testid="portfolio-board"]
${PORTFOLIO_NEW_INITIATIVE}         css=[data-testid="new-initiative"]
${PORTFOLIO_NEW_TITLE}              css=[data-testid="new-initiative-title"]
${PORTFOLIO_NEW_DESCRIPTION}        css=[data-testid="new-initiative-description"]
${PORTFOLIO_NEW_UNIT}               css=[data-testid="new-initiative-unit"]
${PORTFOLIO_NEW_SUBMIT}             css=[data-testid="new-initiative-submit"]
${PORTFOLIO_MODE_LIST}              css=[data-testid="mode-list"]
${PORTFOLIO_LIST}                   css=[data-testid="portfolio-list"]
# Cartão pelo título exato, dentro da coluna do status: format com a chave do status e o título.
${PORTFOLIO_CARD_IN_COLUMN}         css=[data-testid="column-{status}"] article:has(.title:text-is("{title}"))
${PORTFOLIO_CARD_MENU}              css=[data-testid="card-menu"]
${PORTFOLIO_MOVE_TO}                css=[data-testid="move-to-{status}"]
${PORTFOLIO_CONFIGURE_BOARD}        css=[data-testid="configure-board"]
${PORTFOLIO_CONFIG_PANEL}           css=[data-testid="board-config"]
${PORTFOLIO_CONFIG_FIRST_WIP}       css=[data-testid="board-config-column"] >> nth=0 >> css=[data-testid="board-config-wip-limit"]
${PORTFOLIO_CONFIG_SAVE}            css=[data-testid="board-config-save"]
${PORTFOLIO_WIP_IDEA}               css=[data-testid="wip-IDEA"]
${PORTFOLIO_CARD_BLOCK}             css=[data-testid="card-block"]
${PORTFOLIO_CARD_UNBLOCK}           css=[data-testid="card-unblock"]
${PORTFOLIO_BLOCK_REASON}           css=[data-testid="block-reason"]
${PORTFOLIO_BLOCK_CONFIRM}          css=[data-testid="block-confirm"]
${PORTFOLIO_CARD_BLOCKED_BADGE}     css=[data-testid="card-blocked"]
