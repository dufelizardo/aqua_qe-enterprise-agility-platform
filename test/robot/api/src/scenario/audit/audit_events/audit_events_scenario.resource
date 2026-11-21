*** Settings ***
Resource            ../../../flow/audit/audit_events/audit_events_flow.resource

*** Keywords ***
AUDIT - GET AUDIT EVENTS - GET
    [Documentation]    Cenário de GET /api/v1/audit-events pelo status esperado.
    [Arguments]    ${meuHTTP}    ${title}    ${wait}
    Audit - Get Audit Events - GET - By HTTP Status Code    ${meuHTTP}    ${title}    ${wait}
