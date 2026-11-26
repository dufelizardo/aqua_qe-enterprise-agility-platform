*** Settings ***
Documentation       Gates por transição e aprovações (SSD 5; #78). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
PUT Gate As Persona
    [Arguments]    ${persona}    ${transition}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    PUT On Session    ${session}    ${API_BASE_PATH}/workflow-transitions/${transition}/gate
    ...    json=${payload}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

DELETE Gate As Persona
    [Arguments]    ${persona}    ${transition}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    DELETE On Session    ${session}    ${API_BASE_PATH}/workflow-transitions/${transition}/gate
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Gate Panel As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/gates    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Approval As Persona
    [Arguments]    ${persona}    ${artifact}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/approvals    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
