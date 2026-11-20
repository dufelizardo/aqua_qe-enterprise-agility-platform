*** Settings ***
Documentation       Sprints (SSD 3.9). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
POST Sprint As Persona
    [Arguments]    ${persona}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/sprints    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Sprint Action As Persona
    [Arguments]    ${persona}    ${sprint_id}    ${action}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/sprints/${sprint_id}/${action}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PUT Sprint Item As Persona
    [Arguments]    ${persona}    ${sprint_id}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PUT On Session    ${session}    ${API_BASE_PATH}/sprints/${sprint_id}/items/${artifact}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Sprint Items As Persona
    [Arguments]    ${persona}    ${sprint_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/sprints/${sprint_id}/items    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
