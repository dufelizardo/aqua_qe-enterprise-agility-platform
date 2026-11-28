*** Settings ***
Documentation       Preferências do usuário (#15): GET e PUT /api/v1/me/preferences. Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET Preferences As Persona
    [Arguments]    ${persona}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/me/preferences    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PUT Preferences As Persona
    [Arguments]    ${persona}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PUT On Session    ${session}    ${API_BASE_PATH}/me/preferences    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
