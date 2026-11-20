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

PORTFOLIO - CONFIGURAR WIP E BLOQUEAR
    [Documentation]    Configura o limite de WIP da coluna Ideia e marca e desmarca uma Iniciativa como bloqueada.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${initiative_base}    ${initiative_description}    ${wip_limit}    ${block_reason}
    ${portfolio}=    Portfolio - Preparar Portfólio    ${username}    ${display_name}    ${root_name}
    ...    ${portfolio_base}    ${portfolio_label}
    ${title}=    Unique Name    ${initiative_base}
    Portfolio - Abrir Quadro
    Portfolio - Criar Iniciativa    ${title}    ${initiative_description}    ${portfolio}
    Portfolio - Configurar Limite De WIP    ${wip_limit}
    Portfolio - Bloquear E Desbloquear Iniciativa    ${title}    ${block_reason}

TACTICAL - CRIAR E MOVER FEATURE
    [Documentation]    Portfólio e Cadeia novos; Iniciativa no Portfolio; Feature sob ela no Tactical, movida de Backlog
    ...    para Em Refinamento.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${initiative_base}    ${feature_base}    ${description}
    ${portfolio}=    Portfolio - Preparar Portfólio    ${username}    ${display_name}    ${root_name}
    ...    ${portfolio_base}    ${portfolio_label}
    ${cadeia}=    Unique Name    ${cadeia_base}
    Admin - Criar Unidade Sob    ${portfolio}    ${cadeia}    ${cadeia_label}
    ${initiative}=    Unique Name    ${initiative_base}
    Portfolio - Abrir Quadro
    Portfolio - Criar Iniciativa    ${initiative}    ${description}    ${portfolio}
    ${key}=    Get Card Key    IDEA    ${initiative}
    ${feature}=    Unique Name    ${feature_base}
    Tactical - Abrir Quadro
    Tactical - Criar Feature Sob Iniciativa    ${feature}    ${description}    ${key} · ${initiative}    ${cadeia}
    Portfolio - Mover Iniciativa    ${feature}    BACKLOG    REFINEMENT
