*** Settings ***
Documentation       Transições e histórico de status (SSD 3.5). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Variables ***
${WORKFLOW_ARTIFACTS_ENDPOINT}      /artifacts

*** Keywords ***
GET Transitions As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${WORKFLOW_ARTIFACTS_ENDPOINT}/${artifact}/transitions
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Transition As Persona
    [Arguments]    ${persona}    ${artifact}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}${WORKFLOW_ARTIFACTS_ENDPOINT}/${artifact}/transitions
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET History As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${WORKFLOW_ARTIFACTS_ENDPOINT}/${artifact}/history
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
