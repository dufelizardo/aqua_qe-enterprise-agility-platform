*** Settings ***
Documentation       Busca (GET /search, #84). Chamadas HTTP cruas, sem asserções.
Resource            ../common/aquaqe_api_common.resource

*** Keywords ***
GET Search As Persona
    [Arguments]    ${persona}    ${q}    ${kind}=${None}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${params}=    Create Dictionary    q=${q}
    IF    $kind is not None
        Set To Dictionary    ${params}    kind=${kind}
    END
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/search    params=${params}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
