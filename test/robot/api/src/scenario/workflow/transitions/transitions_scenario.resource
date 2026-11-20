*** Settings ***
Resource            ../../../flow/workflow/transitions/transitions_flow.resource

*** Keywords ***
WORKFLOW - GET TRANSITIONS - GET
    [Documentation]    Cenário de GET /api/v1/artifacts/{artifact}/transitions.
    [Arguments]    ${title}
    GET Transitions - Status Code 200    ${title}

WORKFLOW - POST TRANSITION - POST
    [Documentation]    Cenário de POST /api/v1/artifacts/{artifact}/transitions pelo status esperado.
    [Arguments]    ${meuHTTP}    ${title}    ${comment}
    Workflow - Post Transition - POST - By HTTP Status Code    ${meuHTTP}    ${title}    ${comment}
