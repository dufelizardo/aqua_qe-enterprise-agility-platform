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
    WIKI UI - CRIAR E EDITAR PAGINA    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_root_name}
    ...    ${wiki_page_base}    ${wiki_text}    ${wiki_summary}    ${wiki_screenshot_page}

CT-002 - KNOW-009: Validate Regimento - não é aprovado sem as seções obrigatórias
    [Documentation]    Regimento independente do Wiki (bug #66): quem aprova recebe o aviso das seções que faltam; o
    ...    regimento volta a rascunho.
    [Tags]    UI    Regimento    KNOW-009    KNOW-028    BUG-66
    WIKI UI - REGIMENTO EXIGE SECOES    ${wiki_admin_username}    ${wiki_admin_display_name}    ${wiki_root_name}
    ...    ${wiki_unit_base}    ${wiki_unit_label}    ${wiki_regimento_base}    ${wiki_screenshot_reg}

CT-003 - KNOW-028: Validate Regimento - quem não é aprovador não vê Aprovar
    [Documentation]    Com o regimento em revisão, o PM vê o status, mas a aprovação é do BUSINESS_OWNER.
    [Tags]    UI    Regimento    KNOW-028    security    BUG-66
    WIKI UI - SEM APROVACAO PARA O PM    ${wiki_admin_username}    ${wiki_admin_display_name}    ${wiki_root_name}
    ...    ${wiki_unit_base}    ${wiki_unit_label}    ${wiki_regimento_base}    ${wiki_pm_username}
    ...    ${wiki_pm_display_name}

CT-004 - KNOW-006: Validate Wiki - reorganizar páginas (Subir e Mover para…)
    [Documentation]    PROP-002: a segunda página sobe para antes da primeira e depois vai para dentro dela.
    [Tags]    UI    Wiki    KNOW-006    PROP-002
    WIKI UI - ORGANIZAR PAGINAS    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_root_name}
    ...    ${wiki_page_base}

CT-005 - KNOW-006: Validate Wiki - arrastar e soltar página na árvore
    [Documentation]    PROP-002: a terceira página, arrastada sobre a primeira, fica antes dela.
    [Tags]    UI    Wiki    KNOW-006    PROP-002
    WIKI UI - ARRASTAR PAGINA    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_root_name}
    ...    ${wiki_page_base}

CT-006 - KNOW-006: Validate Wiki - menu ⋯ renomeia, faz uma cópia e arquiva
    [Documentation]    O menu ⋯ da página reúne as ações: renomear, fazer uma cópia (logo depois) e arquivar.
    [Tags]    UI    Wiki    KNOW-006
    WIKI UI - MENU DA PAGINA    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_root_name}
    ...    ${wiki_page_base}    ${wiki_renamed_suffix}

CT-007 - KNOW-020: Validate Wiki - rascunho automático e aviso ao sair sem salvar
    [Documentation]    #82: o texto digitado fica como rascunho; sair pergunta; editar de novo recupera; salvar apaga.
    [Tags]    UI    Wiki    KNOW-020
    WIKI UI - RASCUNHO AUTOMATICO    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_root_name}
    ...    ${wiki_page_base}    ${wiki_draft_text}

CT-008 - HOME-001: Validate Home - continuar de onde parou e novidades
    [Documentation]    #83: a página criada aparece nos recentes e nas novidades da Home; o link reabre a página.
    [Tags]    UI    Home    HOME-001    HOME-002
    WIKI UI - CONTINUAR PELA HOME    ${wiki_pm_username}    ${wiki_pm_display_name}    ${wiki_root_name}
    ...    ${wiki_page_base}
