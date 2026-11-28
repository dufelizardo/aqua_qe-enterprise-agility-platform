*** Settings ***
Documentation       Preferências do usuário (#15; REV-10): tema e avisos do sino. O fluxo sempre devolve os padrões
...                 no fim, para não mudar o tema das suítes de UI. O filtro dos avisos é coberto no JUnit
...                 CommentsIntegrationTests.
Resource            ../../../pom/identity/preferences/preferences_pom.resource

*** Keywords ***
Preferencias - Gravar E Ler
    [Documentation]    Grava tema e avisos, lê de volta; tema inválido é 400 no campo theme.
    [Arguments]    ${persona}    ${theme}
    ${notifications}=    Create Dictionary    mention=${False}    reply=${True}
    ${payload}=    Create Dictionary    theme=${theme}    notifications=${notifications}
    ${saved}=    PUT Preferences As Persona    ${persona}    ${payload}
    Should Be Equal As Integers    ${saved.status_code}    200
    Should Be Equal    ${saved.json()}[theme]    ${theme}
    ${read}=    GET Preferences As Persona    ${persona}
    Should Be Equal As Integers    ${read.status_code}    200
    Should Be Equal    ${read.json()}[theme]    ${theme}
    Should Not Be True    ${read.json()}[notifications][mention]
    Should Be True    ${read.json()}[notifications][reply]
    ${invalid}=    Create Dictionary    theme=ROXO
    ${refused}=    PUT Preferences As Persona    ${persona}    ${invalid}
    Should Be Equal As Integers    ${refused.status_code}    400
    Should Be Equal    ${refused.json()}[code]    VALIDATION_FAILED
    Should Be Equal    ${refused.json()}[errors][0][field]    theme

Preferencias - Voltar Ao Padrao
    [Documentation]    Limpeza: tema do sistema e todos os avisos ligados.
    [Arguments]    ${persona}
    ${notifications}=    Create Dictionary    mention=${True}    reply=${True}
    ${payload}=    Create Dictionary    theme=SYSTEM    notifications=${notifications}
    ${response}=    PUT Preferences As Persona    ${persona}    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
