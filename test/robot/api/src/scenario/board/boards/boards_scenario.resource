*** Settings ***
Resource            ../../../flow/board/boards/boards_flow.resource

*** Keywords ***
BOARD - PATCH BOARD - PATCH
    [Documentation]    Cenário de PATCH /api/v1/boards/{id} pelo status esperado.
    [Arguments]    ${meuHTTP}
    Board - Patch Board - PATCH - By HTTP Status Code    ${meuHTTP}

BOARD - WIP LIMIT - POST TRANSITION
    [Documentation]    Cenário do limite de WIP com política de impedir.
    POST Transition - Status Code 422 - Limite De WIP
