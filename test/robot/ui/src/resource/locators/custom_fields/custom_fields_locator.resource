*** Comments ***
    Locators dos campos customizados (#17; REV-12): o catálogo em Administração › Campos customizados e o campo na
    página do artefato. Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${CUSTOM_FIELDS_TYPE}               css=[data-testid="cf-type"]
${CUSTOM_FIELDS_NEW_LABEL}          css=[data-testid="cf-new-label"]
${CUSTOM_FIELDS_NEW_KEY}            css=[data-testid="cf-new-key"]
${CUSTOM_FIELDS_NEW_TYPE}           css=[data-testid="cf-new-type"]
${CUSTOM_FIELDS_NEW_OPTIONS}        css=[data-testid="cf-new-options"]
${CUSTOM_FIELDS_ADD}                css=[data-testid="cf-add"]
# Linha do catálogo pela chave: format com a chave.
${CUSTOM_FIELDS_ROW_BY_KEY}         css=[data-testid="cf-row"][data-key="{}"]
${CUSTOM_FIELDS_REMOVE}             css=[data-testid="cf-remove"]
${CUSTOM_FIELDS_REMOVE_OK}          css=[data-testid="cf-remove-ok"]
# Campo na página do artefato: format com a chave.
${ARTIFACT_CUSTOM_FIELD_BY_KEY}     css=[data-testid="artifact-custom-{}"]
${ARTIFACT_CUSTOM_SELECT}           css=[data-testid="artifact-custom-select"]
