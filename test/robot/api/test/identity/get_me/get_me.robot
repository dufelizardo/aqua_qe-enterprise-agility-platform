*** Settings ***
Documentation       Suíte de GET /api/v1/me (SSD, seção 3.1).
Resource            ../../../src/scenario/identity/get_me/get_me_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    GET Me
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-036: Validate GET Me - HTTP 200 OK (todas as personas)
    [Documentation]    Cada persona de teste recebe os próprios dados com o token emitido pelo Keycloak.
    [Tags]    GET    GetMe    HTTP200    PRD-RF-036    smoke
    [Template]    IDENTITY - GET ME - GET
    200    admin
    200    bo
    200    pm
    200    tl
    200    ux
    200    qa
    200    squad
    200    viewer

CT-002 - PRD-RF-036: Validate GET Me - HTTP 401 UNAUTHORIZED (sem token)
    [Documentation]    Sem token nem sessão a API responde 401, sem redirecionar.
    [Tags]    GET    GetMe    HTTP401    PRD-RF-036    security    ASVS-V4
    IDENTITY - GET ME - GET    401

CT-003 - PRD-RF-036: Validate GET Me - HTTP 401 UNAUTHORIZED (token inválido)
    [Documentation]    Token com assinatura inválida é recusado.
    [Tags]    GET    GetMe    HTTP401    PRD-RF-036    security    ASVS-V3
    IDENTITY - GET ME - GET - Token Invalido    ${get_me_invalid_token}
