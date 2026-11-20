*** Settings ***
Documentation       Suíte de saúde da aplicação (sondas do Kubernetes).
Resource            ../../../src/scenario/actuator/get_health/get_health_scenario.resource
Metadata            Suite    GET Health
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - SAD-7.9: Validate GET Health - HTTP 200 OK
    [Documentation]    Saúde geral, liveness e readiness respondem UP sem autenticação.
    [Tags]    GET    Health    HTTP200    smoke
    ACTUATOR - HEALTH - GET
