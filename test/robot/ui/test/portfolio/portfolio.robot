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
