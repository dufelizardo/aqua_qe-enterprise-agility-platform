*** Comments ***
    Locators da Administração › Workflows (#49). Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${WORKFLOWS_TAB}                    css=[data-testid="admin-tab-workflows"]
${WORKFLOWS_UNIT_SELECT}            css=[data-testid="wf-unit"]
# Opção da unidade pelo nome exato: format com o nome.
${WORKFLOWS_UNIT_OPTION_BY_NAME}    css=[data-testid="wf-unit"] option[data-name="{}"]
# Linha do workflow pelo nome exato: format com o nome.
${WORKFLOWS_ROW_BY_NAME}            css=[data-testid="wf-row"][data-name="{}"]
${WORKFLOWS_ROW_VERSION}            css=[data-testid="wf-version"]
${WORKFLOWS_ROW_ORIGIN}             css=[data-testid="wf-origin"]
${WORKFLOWS_CUSTOMIZE}              css=[data-testid="wf-customize"]
${WORKFLOWS_REVERT}                 css=[data-testid="wf-revert"]
${WORKFLOWS_REVERT_OK}              css=[data-testid="wf-revert-ok"]
${WORKFLOWS_EDITOR}                 css=[data-testid="wf-editor"]
${WORKFLOWS_NEW_STATUS_NAME}        css=[data-testid="wf-new-status-name"]
${WORKFLOWS_ADD_STATUS}             css=[data-testid="wf-add-status"]
# Linha do status no editor pela chave: format com a chave.
${WORKFLOWS_STATUS_ROW_BY_KEY}      css=[data-testid="wf-status-row"][data-key="{}"]
# Caixa da transição: format com "DE-PARA" (chaves).
${WORKFLOWS_TRANSITION}             css=[data-testid="wf-transition-{}"]
${WORKFLOWS_PUBLISH}                css=[data-testid="wf-publish"]
${WORKFLOWS_PUBLISH_OK}             css=[data-testid="wf-publish-ok"]
