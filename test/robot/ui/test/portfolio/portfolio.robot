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
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${portfolio_initiative_base}    ${portfolio_initiative_description}

CT-002 - KAN-006: Validate Portfolio - configurar limite de WIP e marcar Iniciativa como bloqueada
    [Documentation]    "Configurar quadro" define o limite (contador n / limite); o cartão bloqueado mostra a etiqueta.
    [Tags]    UI    Portfolio    PRD-RF-011    KAN-002    KAN-006    KAN-009
    PORTFOLIO - CONFIGURAR WIP E BLOQUEAR    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${portfolio_initiative_base}    ${portfolio_initiative_description}    ${portfolio_wip_limit}
    ...    ${portfolio_block_reason}

CT-003 - PRD-RF-012: Validate Tactical - criar Feature sob a Iniciativa e mover de Backlog para Em Refinamento
    [Documentation]    O quadro de FL2 usa o mesmo componente; a Feature nasce em Backlog com a Iniciativa como pai.
    [Tags]    UI    Tactical    PRD-RF-010    PRD-RF-011    PRD-RF-012
    TACTICAL - CRIAR E MOVER FEATURE    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${portfolio_initiative_base}    ${tactical_feature_base}    ${portfolio_initiative_description}

CT-004 - PRD-RF-015: Validate Squad - sprint criada, com item, iniciada e encerrada
    [Documentation]    Abas Backlog e Sprints da tela de FL1; o encerramento avisa quantos itens voltam ao backlog.
    [Tags]    UI    Squad    Sprint    PRD-RF-015
    SQUAD - SPRINT COMPLETA    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${squad_task_base}    ${squad_sprint_base}

CT-005 - PRD-RF-018: Validate Iniciativa - abrir pelo cartão, documentar e aprovar o PRD
    [Documentation]    #63: a página do artefato tem as abas dos documentos; o PRD salvo fica Em andamento e, aprovado,
    ...    aparece como Aprovado na Visão geral.
    [Tags]    UI    Portfolio    ArtifactDocuments    PRD-RF-018    PRD-RF-019    PRD-RF-020
    PORTFOLIO - DOCUMENTOS DA INICIATIVA    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${portfolio_initiative_base}    ${portfolio_initiative_description}    ${portfolio_document_text}

CT-006 - PRD-RF-010: Validate Iniciativa - editar título e Responsável na página do artefato
    [Documentation]    Clicar no título e no Responsável edita cada campo; o quadro mostra o título novo.
    [Tags]    UI    Portfolio    ArtifactEdit    PRD-RF-010
    PORTFOLIO - EDITAR DADOS DA INICIATIVA    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${portfolio_initiative_base}    ${portfolio_initiative_description}    ${portfolio_renamed_suffix}

CT-007 - PRD-RF-023: Validate Gate - aprovação de Tech Lead libera o Mover nos Próximos passos
    [Documentation]    #78: o gate criado em Admin › Gates bloqueia o Mover até a aprovação; aprovado, a Iniciativa muda
    ...    de status. O gate é removido no fim.
    [Tags]    UI    Portfolio    Gates    PRD-RF-022    PRD-RF-023    PRD-RF-024
    PORTFOLIO - GATE COM APROVACAO    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${portfolio_initiative_base}    ${portfolio_initiative_description}    ${gate_ui_name}    ${gate_ui_message}
    ...    ${gate_ui_path}    ${gate_ui_to_key}    ${gate_ui_to_name}

CT-008 - SSD-3.4: Validate Responsável padrão - "Quem criou" preenche o Owner da nova Iniciativa
    [Documentation]    #43: a regra da Squad em Admin › Responsável padrão sugere o Owner no formulário; a Iniciativa
    ...    nasce com quem criou; "Seguir a de cima" volta à regra herdada.
    [Tags]    UI    Portfolio    DefaultOwner    SSD-3.4
    PORTFOLIO - RESPONSAVEL PADRAO    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${squad_level_name}
    ...    ${portfolio_initiative_base}    ${portfolio_initiative_description}

CT-009 - SSD-3.9: Validate Templates de quadro - criar pelo template, salvar como template e remover
    [Documentation]    #50: o quadro da Squad nasce com as colunas do "Kanban — Sustentação"; o quadro vira template;
    ...    o administrador o remove no catálogo.
    [Tags]    UI    Portfolio    BoardTemplates    SSD-3.9
    PORTFOLIO - TEMPLATES DE QUADRO    ${portfolio_admin_username}    ${portfolio_admin_display_name}
    ...    ${portfolio_root_name}    ${portfolio_unit_base}    ${portfolio_unit_label}    ${tactical_cadeia_base}
    ...    ${tactical_cadeia_label}    ${squad_unit_base}    ${squad_unit_label}    ${board_template_ui_name}
    ...    ${board_template_ui_first_key}    ${board_template_ui_first_column}    ${board_template_ui_new_base}
