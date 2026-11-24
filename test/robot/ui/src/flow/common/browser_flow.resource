*** Settings ***
Documentation       Navegador das suítes de interface, para os cenários abrirem e fecharem sem chamar o POM direto
...                 (LKDF: SCENARIO -> FLOW -> POM; PROP-003, LKDF-QA-003).
Resource            ../../pom/common/aquaqe_ui_common.resource

*** Keywords ***
Navegador - Abrir
    [Documentation]    Navegador novo, sem sessão, no caminho informado.
    [Arguments]    ${path}=/
    Open AQuA-QE Browser    ${path}

Navegador - Abrir Celular
    [Documentation]    Como Navegador - Abrir, com a tela de um celular.
    [Arguments]    ${path}=/
    Open AQuA-QE Mobile Browser    ${path}

Navegador - Fechar
    Close AQuA-QE Browser
