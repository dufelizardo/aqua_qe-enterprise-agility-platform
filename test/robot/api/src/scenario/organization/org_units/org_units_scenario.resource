*** Settings ***
Resource            ../../../flow/organization/org_units/org_units_flow.resource

*** Keywords ***
ORGANIZATION - GET ORG LEVELS - GET
    [Documentation]    Cenário de GET /api/v1/org-levels.
    [Arguments]    ${persona}
    GET Org Levels - Status Code 200    ${persona}

ORGANIZATION - POST ORG UNIT - POST
    [Documentation]    Cenário de POST /api/v1/org-units pelo status esperado.
    [Arguments]    ${meuHTTP}    ${persona}    ${name}
    Organization - Post Org Unit - POST - By HTTP Status Code    ${meuHTTP}    ${persona}    ${name}

ORGANIZATION - GET ORG UNIT - GET - Inexistente
    [Documentation]    Cenário de GET /api/v1/org-units/{id} com id inexistente.
    [Arguments]    ${persona}    ${unit_id}
    GET Org Unit - Status Code 404    ${persona}    ${unit_id}

ORGANIZATION - PUT ORG UNIT LINK - PUT
    [Documentation]    Cenário de PUT /api/v1/org-units/{id}/links/{targetId} pelo status esperado.
    [Arguments]    ${meuHTTP}    ${persona}    ${portfolio_name}    ${cadeia_name}
    Organization - Put Org Unit Link - PUT - By HTTP Status Code    ${meuHTTP}    ${persona}    ${portfolio_name}
    ...    ${cadeia_name}
