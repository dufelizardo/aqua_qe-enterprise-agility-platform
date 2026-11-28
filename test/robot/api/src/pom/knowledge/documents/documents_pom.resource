*** Settings ***
Documentation       Documentos da Iniciativa e da Feature (SSD 3.7; #63). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
GET Artifact Documents As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/documents    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Artifact Document As Persona
    [Arguments]    ${persona}    ${artifact}    ${type}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/documents/${type}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

PUT Artifact Document As Persona
    [Arguments]    ${persona}    ${artifact}    ${type}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PUT On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/documents/${type}
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Artifact Document Status As Persona
    [Arguments]    ${persona}    ${artifact}    ${type}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/documents/${type}/status
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Artifact Document Versions As Persona
    [Arguments]    ${persona}    ${artifact}    ${type}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/documents/${type}/versions
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Artifact Document Markdown As Persona
    [Arguments]    ${persona}    ${artifact}    ${type}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/documents/${type}/export/markdown
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.headers}
    RETURN    ${response}
