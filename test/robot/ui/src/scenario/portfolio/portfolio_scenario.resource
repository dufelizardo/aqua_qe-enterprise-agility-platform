*** Settings ***
Resource            ../../flow/portfolio/portfolio_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER FOR PORTFOLIO
    [Documentation]    Navegador novo, sem cookies, direto em /home (o login leva de volta para lá).
    Open AQuA-QE Browser    /home

TEARDOWN: CLOSE PORTFOLIO BROWSER
    Close AQuA-QE Browser

PORTFOLIO - CRIAR E MOVER INICIATIVA
    [Documentation]    Cria um Portfólio, abre o quadro, cria a Iniciativa e a move de Ideia para Backlog.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${initiative_base}    ${initiative_description}
    ${portfolio}=    Portfolio - Preparar Portfólio    ${username}    ${display_name}    ${root_name}
    ...    ${portfolio_base}    ${portfolio_label}
    ${title}=    Unique Name    ${initiative_base}
    Portfolio - Abrir Quadro
    Portfolio - Criar Iniciativa    ${title}    ${initiative_description}    ${portfolio}
    Portfolio - Mover Iniciativa    ${title}    IDEA    BACKLOG
    Portfolio - Lista Mostra A Iniciativa    ${title}
