*** Settings ***
Documentation       GET /actuator/health e as sondas do Kubernetes (liveness e readiness).
Resource            ../../../pom/actuator/get_health/get_health_pom.resource
Resource            ../../common/schema_validation_flow.resource
Resource            ../../../resource/config/schema/schema.resource

*** Keywords ***
GET Health - Status Code 200
    [Documentation]    A saúde geral e as duas sondas respondem UP sem autenticação.
    FOR    ${suffix}    IN    ${EMPTY}    /liveness    /readiness
        ${response}=    GET Health    ${suffix}
        Should Be Equal As Integers    ${response.status_code}    200
        Validate Object Schema    ${response.json()}    ${ACTUATOR_HEALTH_SCHEMA}
    END
