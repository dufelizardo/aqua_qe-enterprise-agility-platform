*** Settings ***
Resource            ../../flow/preferences/preferences_flow.resource
Resource            ../../flow/common/browser_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER FOR PREFERENCES
    [Documentation]    Navegador novo, sem cookies, direto numa tela interna (/home).
    Navegador - Abrir    /home

TEARDOWN: CLOSE PREFERENCES BROWSER
    Navegador - Fechar

PREFERENCES - TEMA E AVISOS
    [Documentation]    Jornada: abrir Preferências pelo menu, escolher o tema escuro e desligar a menção.
    [Arguments]    ${username}    ${display_name}
    Preferencias - Abrir Pelo Menu    ${username}    ${display_name}
    Preferencias - Tema Escuro E Mencao Desligada
