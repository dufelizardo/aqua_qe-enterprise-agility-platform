*** Settings ***
Documentation       GET /actuator/health (pública; usada pelas sondas do Kubernetes). Chamada HTTP crua, sem asserções.
Resource            ../../common/aquaqe_api_common.resource

*** Variables ***
${ACTUATOR_HEALTH_ENDPOINT}     /actuator/health

*** Keywords ***
GET Health
    [Arguments]    ${path_suffix}=${EMPTY}
    ${session}=    Get AQuA-QE Anonymous Session
    ${response}=    GET On Session    ${session}    ${ACTUATOR_HEALTH_ENDPOINT}${path_suffix}    expected_status=ANY
    Log    ${response.status_code} ${response.text}
    RETURN    ${response}
