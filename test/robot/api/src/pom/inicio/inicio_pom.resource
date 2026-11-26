*** Settings ***
Documentation       Home (#83): itens abertos por último e novidades. Chamadas HTTP cruas, sem asserções.
Resource            ../common/aquaqe_api_common.resource

*** Keywords ***
POST Recent Visit As Persona
    [Arguments]    ${persona}    ${type}    ${ref}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${payload}=    Create Dictionary    type=${type}    ref=${ref}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/me/recent    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Recent As Persona
    [Arguments]    ${persona}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/me/recent    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET News As Persona
    [Arguments]    ${persona}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/me/news    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
