*** Settings ***
Resource            ../../../flow/board/sprints/sprints_flow.resource

*** Keywords ***
BOARD - SPRINT - POST
    [Documentation]    Cenário de sprints pelo status esperado.
    [Arguments]    ${meuHTTP}    ${name}
    Board - Post Sprint - POST - By HTTP Status Code    ${meuHTTP}    ${name}
