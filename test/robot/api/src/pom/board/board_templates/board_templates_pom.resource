*** Settings ***
Documentation       Templates de quadro (#50; SSD 3.9). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET Board Templates As Persona
    [Arguments]    ${persona}    ${level}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${params}=    Create Dictionary    level=${level}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/board-templates    params=${params}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Board Template From Board As Persona
    [Arguments]    ${persona}    ${board_id}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/boards/${board_id}/template    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PATCH Board Template As Persona
    [Arguments]    ${persona}    ${template_id}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PATCH On Session    ${session}    ${API_BASE_PATH}/board-templates/${template_id}    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

DELETE Board Template As Persona
    [Arguments]    ${persona}    ${template_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    DELETE On Session    ${session}    ${API_BASE_PATH}/board-templates/${template_id}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
