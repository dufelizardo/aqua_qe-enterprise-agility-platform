*** Settings ***
Documentation       Páginas de conhecimento por unidade (SSD 3.7.1; Proposta 07). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
POST Page As Persona
    [Arguments]    ${persona}    ${org_unit}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/org-units/${org_unit}/pages    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Regimento As Persona
    [Arguments]    ${persona}    ${org_unit}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/org-units/${org_unit}/regimento    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Pages As Persona
    [Arguments]    ${persona}    ${org_unit}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/org-units/${org_unit}/pages    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Page As Persona
    [Arguments]    ${persona}    ${page}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/pages/${page}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PUT Page As Persona
    [Arguments]    ${persona}    ${page}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PUT On Session    ${session}    ${API_BASE_PATH}/pages/${page}    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Page Status As Persona
    [Arguments]    ${persona}    ${page}    ${status}    ${comment}=${None}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${payload}=    Create Dictionary    status=${status}    comment=${comment}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/pages/${page}/status    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Page Versions As Persona
    [Arguments]    ${persona}    ${page}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/pages/${page}/versions    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Page Rank As Persona
    [Arguments]    ${persona}    ${page}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/pages/${page}/rank    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
