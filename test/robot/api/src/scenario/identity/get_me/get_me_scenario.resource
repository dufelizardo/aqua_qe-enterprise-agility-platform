*** Settings ***
Resource            ../../../flow/identity/get_me/get_me_flow.resource

*** Keywords ***
IDENTITY - GET ME - GET
    [Documentation]    Cenário de GET /api/v1/me pelo status esperado.
    [Arguments]    ${meuHTTP}    ${persona}=pm
    Identity - Get Me - GET - By HTTP Status Code    ${meuHTTP}    ${persona}

IDENTITY - GET ME - GET - Token Invalido
    [Documentation]    Cenário de GET /api/v1/me com token de assinatura inválida.
    [Arguments]    ${token}
    GET Me - Status Code 401 - Token Invalido    ${token}
