*** Settings ***
Documentation       Responsável padrão por unidade e tipo (#43; SSD 3.4). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET Default Owner Rules As Persona
    [Arguments]    ${persona}    ${unit}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/org-units/${unit}/default-owner-rules    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PUT Default Owner Rule As Persona
    [Arguments]    ${persona}    ${unit}    ${type}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PUT On Session    ${session}    ${API_BASE_PATH}/org-units/${unit}/default-owner-rules/${type}
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

DELETE Default Owner Rule As Persona
    [Arguments]    ${persona}    ${unit}    ${type}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    DELETE On Session    ${session}    ${API_BASE_PATH}/org-units/${unit}/default-owner-rules/${type}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Default Owner Suggestion As Persona
    [Arguments]    ${persona}    ${unit}    ${type}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${params}=    Create Dictionary    artifactType=${type}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/org-units/${unit}/default-owner    params=${params}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
