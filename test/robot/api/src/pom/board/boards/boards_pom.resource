*** Settings ***
Documentation       Quadros (SSD 3.9). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Variables ***
${BOARD_BOARDS_ENDPOINT}    /boards

*** Keywords ***
POST Board As Persona
    [Arguments]    ${persona}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}${BOARD_BOARDS_ENDPOINT}    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PATCH Board As Persona
    [Arguments]    ${persona}    ${board_id}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PATCH On Session    ${session}    ${API_BASE_PATH}${BOARD_BOARDS_ENDPOINT}/${board_id}
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Board View As Persona
    [Arguments]    ${persona}    ${board_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${BOARD_BOARDS_ENDPOINT}/${board_id}/view
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
