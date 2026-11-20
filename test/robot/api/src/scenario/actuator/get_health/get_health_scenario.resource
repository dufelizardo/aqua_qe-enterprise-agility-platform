*** Settings ***
Resource            ../../../flow/actuator/get_health/get_health_flow.resource

*** Keywords ***
ACTUATOR - HEALTH - GET
    [Documentation]    Cenário de saúde da aplicação.
    GET Health - Status Code 200
