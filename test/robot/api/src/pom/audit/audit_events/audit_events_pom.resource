*** Settings ***
Documentation       Trilha de auditoria (SSD 3.10). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET Audit Events As Persona
    [Arguments]    ${persona}    ${params}=${None}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/audit-events    params=${params}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Audit Verification As Persona
    [Arguments]    ${persona}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/audit-events/verification    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
