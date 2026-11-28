*** Settings ***
Documentation       Anexos dos artefatos (SSD 3.7; #13, REV-08). Chamadas HTTP cruas, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Keywords ***
POST Attachment As Persona
    [Documentation]    Envia o arquivo em multipart, no campo file.
    [Arguments]    ${persona}    ${artifact}    ${file_name}    ${content}    ${content_type}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${bytes}=    Evaluate    $content.encode("utf-8")
    ${part}=    Evaluate    ($file_name, $bytes, $content_type)
    ${files}=    Create Dictionary    file=${part}
    ${response}=    POST On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/attachments
    ...    files=${files}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Attachments As Persona
    [Arguments]    ${persona}    ${artifact}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/artifacts/${artifact}/attachments
    ...    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}

GET Attachment Content As Persona
    [Arguments]    ${persona}    ${id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    GET On Session    ${session}    ${API_BASE_PATH}/attachments/${id}/content    expected_status=ANY
    Log    ${response.status_code}
    RETURN    ${response}

DELETE Attachment As Persona
    [Arguments]    ${persona}    ${id}
    ${session}=    Get AQuA-QE Session For Persona    ${persona}
    ${response}=    DELETE On Session    ${session}    ${API_BASE_PATH}/attachments/${id}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
