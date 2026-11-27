*** Comments ***
    Locators dos templates de quadro (#50): escolha ao criar, "Salvar como template" e o catálogo na Administração.
    Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${BOARD_TEMPLATE_SELECT}            css=[data-testid="board-template"]
${BOARD_TEMPLATE_SUMMARY}           css=[data-testid="board-template-summary"]
# Título da coluna pelo primeiro status dela: format com a chave.
${BOARD_COLUMN_TITLE}               css=[data-testid="column-{}"] h3
${BOARD_SAVE_AS_TEMPLATE}           css=[data-testid="board-save-as-template"]
${BOARD_TEMPLATE_NAME}              css=[data-testid="board-template-name"]
${BOARD_TEMPLATE_SAVE}              css=[data-testid="board-template-save"]
${BOARD_TEMPLATE_SAVED}             css=[data-testid="board-template-saved"]
# Catálogo (Administração › Templates de quadro): linha pelo nome exato.
${BOARD_TEMPLATES_ROW_BY_NAME}      css=[data-testid="bt-row"][data-name="{}"]
${BOARD_TEMPLATES_REMOVE}           css=[data-testid="bt-remove"]
${BOARD_TEMPLATES_REMOVE_OK}        css=[data-testid="bt-remove-ok"]
