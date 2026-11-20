*** Settings ***
Documentation       Endpoints de artefatos e da configuração padrão (SSD 3.3 e 3.4). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Variables ***
${WORKITEM_ARTIFACTS_ENDPOINT}          /artifacts
${WORKITEM_ARTIFACT_TYPES_ENDPOINT}     /artifact-types

*** Keywords ***
GET Artifact Types As Persona
    [Arguments]    ${persona}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${WORKITEM_ARTIFACT_TYPES_ENDPOINT}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

POST Artifact As Persona
    [Arguments]    ${persona}    ${payload}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}${WORKITEM_ARTIFACTS_ENDPOINT}    json=${payload}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Artifact As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${WORKITEM_ARTIFACTS_ENDPOINT}/${artifact}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Artifact Context As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${WORKITEM_ARTIFACTS_ENDPOINT}/${artifact}/context
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Artifacts As Persona
    [Arguments]    ${persona}    ${workspace}=${None}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${headers}=    Create Dictionary
    IF    $workspace is not None
        Set To Dictionary    ${headers}    X-Workspace-Id=${workspace}
    END
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}${WORKITEM_ARTIFACTS_ENDPOINT}
    ...    headers=${headers}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

DELETE Artifact As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    DELETE On Session    ${session}    ${API_BASE_PATH}${WORKITEM_ARTIFACTS_ENDPOINT}/${artifact}
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
