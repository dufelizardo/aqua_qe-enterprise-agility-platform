*** Settings ***
Documentation       GET /api/v1/me: escolhe o caminho pelo status esperado e faz as asserções.
Resource            ../../../pom/identity/get_me/get_me_pom.resource
Resource            ../../common/schema_validation_flow.resource
Resource            ../../../resource/config/schema/schema.resource

*** Keywords ***
GET Me - Status Code 200
    [Documentation]    A persona autenticada recebe os próprios dados. As atribuições ainda vêm vazias: entram com o
    ...    módulo access.
    [Arguments]    ${persona}
    ${response}=    GET Me As Persona    ${persona}
    Should Be Equal As Integers    ${response.status_code}    200
    ${body}=    Set Variable    ${response.json()}
    Validate Object Schema    ${body}    ${IDENTITY_ME_SCHEMA}
    Should Be Equal    ${body}[username]    robot.${persona}
    Should Be Equal    ${body}[email]    robot.${persona}@aquaqe.local
    Should Be Empty    ${body}[assignments]
    RETURN    ${response}

GET Me - Status Code 401 - Sem Token
    [Documentation]    Sem token nem sessão, a API responde 401 e não redireciona (o SPA é quem leva ao login).
    ${response}=    GET Me Without Token
    Should Be Equal As Integers    ${response.status_code}    401
    Should Not Contain    ${response.headers}    Location
    RETURN    ${response}

GET Me - Status Code 401 - Token Invalido
    [Documentation]    Um token com assinatura inválida é recusado.
    [Arguments]    ${token}
    ${response}=    GET Me With Token    ${token}
    Should Be Equal As Integers    ${response.status_code}    401
    RETURN    ${response}

Identity - Get Me - GET - By HTTP Status Code
    [Documentation]    Executa o cenário de GET /me correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${persona}=pm
    IF    '${meuHTTP}' == '200'
        GET Me - Status Code 200    ${persona}
    ELSE IF    '${meuHTTP}' == '401'
        GET Me - Status Code 401 - Sem Token
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para GET /me.
    END
