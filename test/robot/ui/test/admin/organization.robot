*** Settings ***
Documentation       Administração › Estrutura organizacional pelo navegador (UX/UI 7.10; SSD 3.2 e 2.1).
Resource            ../../src/scenario/admin/admin_scenario.resource
Resource            ../../src/resource/data_driven/admin/admin_driven.resource
Test Setup          PREREQUISITE: OPEN BROWSER ON ADMIN
Test Teardown       TEARDOWN: CLOSE BROWSER
Metadata            Suite    Admin Organization
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - ADR-14: Validate Admin - criar Portfólio sob a raiz
    [Documentation]    O administrador cria um Portfólio; ele aparece na árvore e fica selecionado.
    [Tags]    UI    Admin    ADR-14    smoke
    ADMIN ORG - CRIAR PORTFOLIO    ${admin_username}    ${admin_display_name}    ${admin_root_name}
    ...    ${admin_portfolio_base}    ${admin_portfolio_label}

CT-002 - ADR-14: Validate Admin - renomear unidade
    [Documentation]    O novo nome aparece no detalhe e na árvore.
    [Tags]    UI    Admin    ADR-14
    ADMIN ORG - RENOMEAR UNIDADE    ${admin_username}    ${admin_display_name}    ${admin_root_name}
    ...    ${admin_portfolio_base}    ${admin_portfolio_label}

CT-003 - REV-04: Validate Admin - Cadeia compartilhada entre Portfólios (cenário C2)
    [Documentation]    A Cadeia sob o Portfólio A passa a atender ao Portfólio B.
    [Tags]    UI    Admin    REV-04
    ADMIN ORG - CADEIA COMPARTILHADA    ${admin_username}    ${admin_display_name}    ${admin_root_name}
    ...    ${admin_portfolio_base}    ${admin_portfolio_label}    ${admin_cadeia_base}    ${admin_cadeia_label}

CT-004 - REV-01: Validate Admin - sem PLATFORM_ADMIN não há acesso à Administração
    [Documentation]    O item Admin não aparece e o endereço leva de volta à home.
    [Tags]    UI    Admin    REV-01    security    ASVS-V4
    ADMIN ORG - SEM ACESSO    ${admin_pm_username}    ${admin_pm_display_name}
