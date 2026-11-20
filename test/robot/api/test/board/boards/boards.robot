*** Settings ***
Documentation       Suíte de quadros configuráveis e limite de WIP (SSD 3.9; PRD-RF-011; Proposta 04).
Resource            ../../../src/scenario/board/boards/boards_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Boards
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-011: Validate PATCH Board - HTTP 200 OK (colunas e WIP configuráveis)
    [Documentation]    Nome, colunas, fases e limite de WIP.
    [Tags]    PATCH    PatchBoard    HTTP200    PRD-RF-011    KAN-002    KAN-006    smoke
    BOARD - PATCH BOARD - PATCH    200

CT-002 - REV-01: Validate PATCH Board - HTTP 403 FORBIDDEN
    [Documentation]    Quem não gere quadros não configura.
    [Tags]    PATCH    PatchBoard    HTTP403    REV-01    security    ASVS-V4
    BOARD - PATCH BOARD - PATCH    403

CT-003 - KAN-006: Validate POST Transition - HTTP 422 GATE_BLOCKED (limite de WIP)
    [Documentation]    A coluna no limite com política de impedir recusa a entrada.
    [Tags]    POST    PostTransition    HTTP422    KAN-006    PRD-RF-011
    BOARD - WIP LIMIT - POST TRANSITION
