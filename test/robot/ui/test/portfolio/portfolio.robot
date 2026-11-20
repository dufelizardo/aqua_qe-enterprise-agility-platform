*** Settings ***
Documentation       Portfolio pelo navegador (UX/UI 7.3 e 6.2; SSD 3.4, 3.5 e 3.9).
Resource            ../../src/scenario/portfolio/portfolio_scenario.resource
Resource            ../../src/resource/data_driven/portfolio/portfolio_driven.resource
Test Setup          PREREQUISITE: OPEN BROWSER FOR PORTFOLIO
Test Teardown       TEARDOWN: CLOSE PORTFOLIO BROWSER
Metadata            Suite    Portfolio
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-001: Validate Portfolio - criar Iniciativa e mover de Ideia para Backlog
    [Documentation]    Quadro do workspace, "Nova Iniciativa" e "Mover para…"; a lista mostra a Iniciativa.
    [Tags]    UI    Portfolio    PRD-RF-001    PRD-RF-011    smoke
    PORTFOLIO - CRIAR E MOVER INICIATIVA    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${portfolio_initiative_base}
    ...    ${portfolio_initiative_description}

CT-002 - KAN-006: Validate Portfolio - configurar limite de WIP e marcar Iniciativa como bloqueada
    [Documentation]    "Configurar quadro" define o limite (contador n / limite); o cartão bloqueado mostra a etiqueta.
    [Tags]    UI    Portfolio    PRD-RF-011    KAN-002    KAN-006    KAN-009
    PORTFOLIO - CONFIGURAR WIP E BLOQUEAR    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${portfolio_initiative_base}
    ...    ${portfolio_initiative_description}    ${portfolio_wip_limit}    ${portfolio_block_reason}

CT-003 - PRD-RF-012: Validate Tactical - criar Feature sob a Iniciativa e mover de Backlog para Em Refinamento
    [Documentation]    O quadro de FL2 usa o mesmo componente; a Feature nasce em Backlog com a Iniciativa como pai.
    [Tags]    UI    Tactical    PRD-RF-010    PRD-RF-011    PRD-RF-012
    TACTICAL - CRIAR E MOVER FEATURE    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${portfolio_initiative_base}    ${tactical_feature_base}
    ...    ${portfolio_initiative_description}
