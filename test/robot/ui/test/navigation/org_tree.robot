*** Settings ***
Documentation       Menu lateral com a árvore organizacional (Proposta 06, Revisão 2; PROPOSAL-07; UX/UI 4).
Resource            ../../src/scenario/navigation/navigation_scenario.resource
Resource            ../../src/resource/data_driven/navigation/navigation_driven.resource
Test Teardown       TEARDOWN: CLOSE NAVIGATION BROWSER
Metadata            Suite    Navigation Org Tree
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - ORG-NAV-001: Validate Menu - escolher a Squad pela árvore leva à tela do Flight Level
    [Documentation]    A busca acha a Squad com o caminho até ela; escolher troca o workspace e abre /squad.
    [Tags]    UI    Navigation    ORG-NAV-001    ORG-NAV-004    ORG-NAV-005    ORG-NAV-016    smoke
    [Setup]    PREREQUISITE: OPEN BROWSER ON NAVIGATION
    NAV ORG - ESCOLHER UNIDADE NA ARVORE    ${nav_username}    ${nav_display_name}    ${nav_root_name}
    ...    ${nav_portfolio_base}    ${nav_portfolio_label}    ${nav_cadeia_base}    ${nav_cadeia_label}
    ...    ${nav_squad_base}    ${nav_squad_label}    ${nav_screenshot_desktop}

CT-002 - ORG-NAV-003: Validate Menu - recolher um ramo e a expansão ficar lembrada
    [Documentation]    Recolher a Cadeia esconde a Squad, e ela continua recolhida depois de recarregar.
    [Tags]    UI    Navigation    ORG-NAV-003    ORG-NAV-015
    [Setup]    PREREQUISITE: OPEN BROWSER ON NAVIGATION
    NAV ORG - RECOLHER E LEMBRAR RAMO    ${nav_username}    ${nav_display_name}    ${nav_root_name}
    ...    ${nav_portfolio_base}    ${nav_portfolio_label}    ${nav_cadeia_base}    ${nav_cadeia_label}
    ...    ${nav_squad_base}    ${nav_squad_label}    ${nav_screenshot_desktop}

CT-003 - ORG-NAV-014: Validate Menu - gaveta com a árvore no celular
    [Documentation]    O menu fica escondido no celular; o botão do menu abre a gaveta e escolher uma unidade a fecha.
    [Tags]    UI    Navigation    ORG-NAV-014    responsive
    [Setup]    PREREQUISITE: OPEN MOBILE BROWSER ON NAVIGATION
    NAV ORG - GAVETA NO CELULAR    ${nav_username}    ${nav_display_name}    ${nav_root_name}
    ...    ${nav_screenshot_mobile}
