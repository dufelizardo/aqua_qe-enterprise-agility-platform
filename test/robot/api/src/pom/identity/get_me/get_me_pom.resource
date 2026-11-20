*** Settings ***
Documentation       GET /api/v1/me (SSD, seção 3.1). Chamada HTTP crua, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Variables ***
${IDENTITY_ME_ENDPOINT}     /me

*** Keywords ***
GET Me As Persona
    [Arguments]    ${persona}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${IDENTITY_ME_ENDPOINT}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Me Without Token
    ${session}=    Get AQuA-QE Anonymous Session
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${IDENTITY_ME_ENDPOINT}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Me With Token
    [Arguments]    ${token}
    ${session}=    Get AQuA-QE Session With Token    ${token}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${IDENTITY_ME_ENDPOINT}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
