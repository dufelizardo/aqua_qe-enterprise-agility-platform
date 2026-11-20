*** Settings ***
Documentation       Sessão HTTP e obtenção de token por persona (doc/QA.md, seção 6). Sem asserções.
Library             RequestsLibrary
Library             Collections

*** Variables ***
${AQUA_HOST_URL}        %{AQUA_HOST_URL=http://localhost:8080}
${AQUA_AUTH_URL}        %{AQUA_AUTH_URL=http://localhost:8180}
${AQUA_REALM}           %{AQUA_REALM=aquaqe-dev}
${AQUA_TEST_PASSWORD}   %{AQUA_TEST_PASSWORD=Robot#Teste123}
${AQUA_ROBOT_CLIENT}    aquaqe-robot
${API_BASE_PATH}        /api/v1

*** Keywords ***
Get AQuA-QE Anonymous Session
    [Documentation]    Sessão sem autenticação, para os casos 401.
    Create Session    aquaqe_anonymous    ${AQUA_HOST_URL}    verify=False
    RETURN    aquaqe_anonymous

Get AQuA-QE Session For Persona
    [Documentation]    Sessão autenticada como a persona de teste robot.<persona> do realm de dev. Cada persona tem a
    ...    própria sessão, com o token no cabeçalho Authorization.
    [Arguments]    ${persona}
    ${token}=    Get Access Token For Persona    ${persona}
    ${headers}=    Create Dictionary    Authorization=Bearer ${token}
    Create Session    aquaqe_${persona}    ${AQUA_HOST_URL}    headers=${headers}    verify=False
    RETURN    aquaqe_${persona}

Get AQuA-QE Session With Token
    [Documentation]    Sessão com um token arbitrário (ex.: inválido), para os casos negativos.
    [Arguments]    ${token}
    ${headers}=    Create Dictionary    Authorization=Bearer ${token}
    Create Session    aquaqe_custom_token    ${AQUA_HOST_URL}    headers=${headers}    verify=False
    RETURN    aquaqe_custom_token

Get Access Token For Persona
    [Documentation]    Pede um token ao Keycloak com usuário e senha da persona (cliente de testes aquaqe-robot).
    [Arguments]    ${persona}
    Create Session    aquaqe_keycloak    ${AQUA_AUTH_URL}    verify=False
    ${form}=    Create Dictionary
    ...    grant_type=password
    ...    client_id=${AQUA_ROBOT_CLIENT}
    ...    username=robot.${persona}
    ...    password=${AQUA_TEST_PASSWORD}
    ${response}=    POST On Session
    ...    aquaqe_keycloak
    ...    /realms/${AQUA_REALM}/protocol/openid-connect/token
    ...    data=${form}
    ...    expected_status=200
    RETURN    ${response.json()}[access_token]
