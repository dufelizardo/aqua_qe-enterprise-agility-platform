*** Settings ***
Documentation       Wiki da unidade pelo navegador (UX/UI 7.12; Proposta 07, Fase A).
Resource            ../../src/scenario/wiki/wiki_scenario.resource
Resource            ../../src/resource/data_driven/wiki/wiki_driven.resource
Test Setup          PREREQUISITE: OPEN BROWSER ON WIKI
Test Teardown       TEARDOWN: CLOSE WIKI BROWSER
Metadata            Suite    Wiki
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - KNOW-006: Validate Wiki - criar página, editar em blocos e ver o histórico
    [Documentation]    A página nova abre na árvore; salvar um parágrafo cria a versão 2; o histórico tem duas versões.
    [Tags]    UI    Wiki    KNOW-006    KNOW-007    KNOW-020    smoke
    WIKI UI - CRIAR E EDITAR PAGINA    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_page_base}
    ...    ${wiki_text}    ${wiki_summary}    ${wiki_screenshot_page}

CT-002 - KNOW-009: Validate Wiki - regimento não é aprovado sem as seções obrigatórias
    [Documentation]    O aprovador (BUSINESS_OWNER) recebe o aviso das seções que faltam; o regimento volta a rascunho.
    [Tags]    UI    Wiki    KNOW-009    KNOW-028
    WIKI UI - REGIMENTO EXIGE SECOES    ${wiki_bo_username}    ${wiki_bo_display_name}    ${wiki_regimento_base}
    ...    ${wiki_screenshot_reg}

CT-003 - KNOW-028: Validate Wiki - quem não é aprovador não vê Aprovar
    [Documentation]    O PM envia o regimento para revisão, mas a aprovação é do BUSINESS_OWNER.
    [Tags]    UI    Wiki    KNOW-028    security
    WIKI UI - SEM APROVACAO PARA O PM    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_regimento_base}
