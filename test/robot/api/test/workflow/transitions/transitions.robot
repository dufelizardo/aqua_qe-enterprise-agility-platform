*** Settings ***
Documentation       Suíte de transições de status e histórico (SSD 3.5 e 4.2). O bloqueio por gate (GATE_BLOCKED) entra
...                 com o módulo governance; até lá é coberto pelo JUnit com uma guarda de teste.
Resource            ../../../src/scenario/workflow/transitions/transitions_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Transitions
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-001: Validate GET Transitions - HTTP 200 OK
    [Documentation]    Transições possíveis a partir do status atual.
    [Tags]    GET    GetTransitions    HTTP200    PRD-RF-001    PRD-RF-019    smoke
    WORKFLOW - GET TRANSITIONS - GET    ${post_transition_title}

CT-002 - PRD-RF-001: Validate POST Transition - HTTP 200 OK (com histórico)
    [Documentation]    Executa Ideia → Backlog e registra no histórico.
    [Tags]    POST    PostTransition    HTTP200    PRD-RF-001    PRD-RF-026    smoke
    WORKFLOW - POST TRANSITION - POST    200    ${post_transition_title}    ${post_transition_comment}

CT-003 - PRD-RF-001: Validate POST Transition - HTTP 409 VERSION_CONFLICT
    [Documentation]    Versão desatualizada é recusada.
    [Tags]    POST    PostTransition    HTTP409    PRD-RF-001
    WORKFLOW - POST TRANSITION - POST    409    ${post_transition_title}    ${post_transition_comment}

CT-004 - PRD-RF-001: Validate POST Transition - HTTP 422 TRANSITION_NOT_ALLOWED
    [Documentation]    Transição que não sai do status atual é recusada.
    [Tags]    POST    PostTransition    HTTP422    PRD-RF-001
    WORKFLOW - POST TRANSITION - POST    422    ${post_transition_title}    ${post_transition_comment}
