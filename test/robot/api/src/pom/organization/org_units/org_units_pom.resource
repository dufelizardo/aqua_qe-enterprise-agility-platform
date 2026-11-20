*** Settings ***
Documentation       Endpoints do módulo organization (SSD, seção 3.2). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Variables ***
${ORGANIZATION_LEVELS_ENDPOINT}     /org-levels
${ORGANIZATION_UNITS_ENDPOINT}      /org-units

*** Keywords ***
GET Org Levels As Persona
    [Arguments]    ${persona}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${ORGANIZATION_LEVELS_ENDPOINT}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Org Units As Persona
    [Arguments]    ${persona}    ${params}=${None}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${ORGANIZATION_UNITS_ENDPOINT}
    ...    params=${params}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Org Unit As Persona
    [Arguments]    ${persona}    ${unit_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${ORGANIZATION_UNITS_ENDPOINT}/${unit_id}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Org Unit As Persona
    [Arguments]    ${persona}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}${ORGANIZATION_UNITS_ENDPOINT}
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PUT Org Unit Link As Persona
    [Arguments]    ${persona}    ${unit_id}    ${target_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PUT On Session    ${session}
    ...    ${API_BASE_PATH}${ORGANIZATION_UNITS_ENDPOINT}/${unit_id}/links/${target_id}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Org Unit Links As Persona
    [Arguments]    ${persona}    ${unit_id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${ORGANIZATION_UNITS_ENDPOINT}/${unit_id}/links
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
