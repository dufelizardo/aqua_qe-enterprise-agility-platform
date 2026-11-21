*** Settings ***
Resource            ../../flow/wiki/wiki_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER ON WIKI
    [Documentation]    Navegador novo, sem cookies, direto em /home (o login leva de volta para lá).
    Open AQuA-QE Browser    /home

TEARDOWN: CLOSE WIKI BROWSER
    Close AQuA-QE Browser

WIKI UI - CRIAR E EDITAR PAGINA
    [Arguments]    ${username}    ${display_name}    ${title_base}    ${text}    ${summary}    ${screenshot}
    Wiki - Abrir    ${username}    ${display_name}
    Wiki - Criar E Editar Pagina    ${title_base}    ${text}    ${summary}    ${screenshot}

WIKI UI - REGIMENTO EXIGE SECOES
    [Arguments]    ${username}    ${display_name}    ${title_base}    ${screenshot}
    Wiki - Abrir    ${username}    ${display_name}
    Wiki - Regimento Exige Secoes Obrigatorias    ${title_base}    ${screenshot}

WIKI UI - SEM APROVACAO PARA O PM
    [Arguments]    ${username}    ${display_name}    ${title_base}
    Wiki - Abrir    ${username}    ${display_name}
    Wiki - Sem Aprovacao Para Quem Nao Aprova    ${title_base}
