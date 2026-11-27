*** Settings ***
Documentation       Workflow Designer (#49; SSD 3.3): workflows por unidade, versões e migração. Chamadas HTTP cruas,
...                 sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET Workflows As Persona
    [Arguments]    ${persona}    ${params}=${None}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/workflows    params=${params}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Workflow As Persona
    [Arguments]    ${persona}    ${workflow}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/workflows/${workflow}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Unit Workflows As Persona
    [Arguments]    ${persona}    ${unit}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/org-units/${unit}/workflows    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Unit Workflow As Persona
    [Arguments]    ${persona}    ${unit}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/org-units/${unit}/workflows    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Workflow Version As Persona
    [Arguments]    ${persona}    ${workflow}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/workflows/${workflow}/versions    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

DELETE Workflow As Persona
    [Arguments]    ${persona}    ${workflow}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    DELETE On Session    ${session}    ${API_BASE_PATH}/workflows/${workflow}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Workflow Migration As Persona
    [Arguments]    ${persona}    ${workflow}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/workflows/${workflow}/migration    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Workflow Migration As Persona
    [Arguments]    ${persona}    ${workflow}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/workflows/${workflow}/migration    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
