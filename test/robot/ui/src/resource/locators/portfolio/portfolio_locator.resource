*** Comments ***
    Locators da tela de Portfolio (UX/UI 7.3). Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${PORTFOLIO_NAV_LINK}               css=[data-testid="nav-portfolio"]
${PORTFOLIO_PAGE_TITLE}             css=[data-testid="page-title"]
${PORTFOLIO_CREATE_BOARD}           css=[data-testid="create-board"]
${PORTFOLIO_BOARD}                  css=[data-testid="board"]
${PORTFOLIO_NEW_INITIATIVE}         css=[data-testid="new-item"]
${PORTFOLIO_NEW_TITLE}              css=[data-testid="new-item-title"]
${PORTFOLIO_NEW_DESCRIPTION}        css=[data-testid="new-item-description"]
${PORTFOLIO_NEW_UNIT}               css=[data-testid="new-item-unit"]
${PORTFOLIO_NEW_SUBMIT}             css=[data-testid="new-item-submit"]
${PORTFOLIO_MODE_LIST}              css=[data-testid="mode-list"]
${PORTFOLIO_LIST}                   css=[data-testid="board-list"]
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
# Telas Tactical e Squad usam o mesmo quadro (UX/UI 7.5 e 7.7).
${LEVEL_NAV_TACTICAL}               css=[data-testid="nav-tactical"]
${LEVEL_NAV_SQUAD}                  css=[data-testid="nav-squad"]
${LEVEL_NEW_PARENT}                 css=[data-testid="new-item-parent"]
${LEVEL_NEW_TYPE}                   css=[data-testid="new-item-type"]
${LEVEL_UNIT_TRAIL}                 css=[data-testid="unit-trail"]
# Abas da tela de FL1 e sprints (UX/UI 7.7).
${SQUAD_NAV}                        css=[data-testid="nav-squad"]
${SQUAD_TAB_BACKLOG}                css=[data-testid="unit-tabs"] a[data-testid="nav-squad-backlog"]
${SQUAD_TAB_SPRINTS}                css=[data-testid="unit-tabs"] a[data-testid="nav-squad-sprints"]
${SPRINTS_SQUAD}                    css=[data-testid="sprints-squad"]
${SPRINT_NAME}                      css=[data-testid="sprint-name"]
${SPRINT_CREATE}                    css=[data-testid="sprint-create"]
${SPRINT_CLOSE_YES}                 css=[data-testid="sprint-close-yes"]
${SPRINT_CLOSE_CONFIRM}             css=[data-testid="sprint-close-confirm"]
# Linha da sprint pelo nome; linha do backlog pelo título.
${SPRINT_ROW_BY_NAME}               css=[data-testid="sprints-list"] > li:has([data-testid="sprint-open"]:text-is("{}"))
${BACKLOG_ROW_BY_TITLE}             css=[data-testid="backlog-item"]:has(.title:text-is("{}"))
