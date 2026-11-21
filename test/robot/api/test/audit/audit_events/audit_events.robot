*** Settings ***
Documentation       Suíte da trilha de auditoria (SSD 3.10; SAD 7.4 e 7.5; PRD-RF-026; REV-03).
Resource            ../../../src/scenario/audit/audit_events/audit_events_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Audit Events
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-026: Validate GET Audit Events - HTTP 200 OK (registro encadeado e trilha íntegra)
    [Documentation]    A criação de uma Iniciativa aparece na trilha com ator, valores e hash; a verificação confere.
    [Tags]    GET    GetAuditEvents    HTTP200    PRD-RF-026    REV-03    smoke
    AUDIT - GET AUDIT EVENTS - GET    200    ${audit_initiative_title}    ${audit_wait}

CT-002 - PRD-RF-026: Validate GET Audit Events - HTTP 403 FORBIDDEN
    [Documentation]    Só o administrador consulta a auditoria.
    [Tags]    GET    GetAuditEvents    HTTP403    PRD-RF-026    security    ASVS-V4
    AUDIT - GET AUDIT EVENTS - GET    403    ${audit_initiative_title}    ${audit_wait}
