*** Settings ***
Documentation       Pessoas e privacidade (#32): /api/v1/admin/people. Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET People Directory As Persona
    [Arguments]    ${persona}    ${q}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${params}=    Create Dictionary    q=${q}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/admin/people    params=${params}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Personal Data As Persona
    [Arguments]    ${persona}    ${person_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/admin/people/${person_id}/personal-data
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.headers}
    RETURN    ${response}

POST Anonymize As Persona
    [Arguments]    ${persona}    ${person_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/admin/people/${person_id}/anonymize
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
