*** Settings ***
Documentation       Suíte de sprints opcionais por squad (SSD 3.9; PRD-RF-015).
Resource            ../../../src/scenario/board/sprints/sprints_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Sprints
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-015: Validate Sprint - criar, incluir, iniciar e encerrar (não concluído volta ao backlog)
    [Documentation]    Ciclo completo de uma sprint.
    [Tags]    POST    Sprint    HTTP201    PRD-RF-015    smoke
    BOARD - SPRINT - POST    201    Robot Sprint

CT-002 - REV-01: Validate Sprint - HTTP 403 FORBIDDEN (membro de squad não gere sprints)
    [Documentation]    Gerir sprints é de quem gere quadros.
    [Tags]    POST    Sprint    HTTP403    REV-01    security    ASVS-V4
    BOARD - SPRINT - POST    403    Robot Sprint

CT-003 - PRD-RF-015: Validate Sprint Start - HTTP 409 SPRINT_ALREADY_ACTIVE
    [Documentation]    Uma squad tem no máximo uma sprint ativa.
    [Tags]    POST    Sprint    HTTP409    PRD-RF-015
    BOARD - SPRINT - POST    409    Robot Sprint
