*** Settings ***
Documentation       Campos customizados por tipo de artefato (#17; REV-12; SSD 3.3). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET Custom Fields As Persona
    [Arguments]    ${persona}    ${type}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${params}=    Create Dictionary    artifactType=${type}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/custom-fields    params=${params}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Custom Field As Persona
    [Arguments]    ${persona}    ${type}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/artifact-types/${type}/custom-fields
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PATCH Custom Field As Persona
    [Arguments]    ${persona}    ${field}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PATCH On Session    ${session}    ${API_BASE_PATH}/custom-fields/${field}    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

DELETE Custom Field As Persona
    [Arguments]    ${persona}    ${field}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    DELETE On Session    ${session}    ${API_BASE_PATH}/custom-fields/${field}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
