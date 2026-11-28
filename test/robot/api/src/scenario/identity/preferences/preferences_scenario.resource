*** Settings ***
Resource            ../../../flow/identity/preferences/preferences_flow.resource

*** Keywords ***
PREFERENCES - SAVE AND READ
    [Documentation]    Cenário de GET e PUT /api/v1/me/preferences.
    [Arguments]    ${persona}    ${theme}
    Preferencias - Gravar E Ler    ${persona}    ${theme}

PREFERENCES - RESET
    [Documentation]    Devolve as preferências padrão da persona.
    [Arguments]    ${persona}
    Preferencias - Voltar Ao Padrao    ${persona}
