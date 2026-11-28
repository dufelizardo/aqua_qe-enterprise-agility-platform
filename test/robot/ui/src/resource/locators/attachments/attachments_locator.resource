*** Comments ***
    Locators da seção Anexos da página do artefato (#13; REV-08). Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${ATTACHMENTS_SECTION}              css=[data-testid="artifact-attachments"]
${ATTACHMENTS_INPUT}                css=[data-testid="attachment-input"]
# Linha do anexo pelo nome do arquivo: format com o nome.
${ATTACHMENTS_ROW_BY_NAME}          css=[data-testid="attachment-row"][data-name="{}"]
${ATTACHMENTS_DOWNLOAD}             css=[data-testid="attachment-download"]
${ATTACHMENTS_REMOVE}               css=[data-testid="attachment-remove"]
${ATTACHMENTS_REMOVE_OK}            css=[data-testid="attachment-remove-ok"]
${ATTACHMENTS_EMPTY}                css=[data-testid="attachment-empty"]
