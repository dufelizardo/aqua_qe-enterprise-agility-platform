*** Settings ***
Documentation       Landing, login e logout pelo navegador (PRD-RF-036; BFF, ADR-16).
Resource            ../../src/scenario/login/login_scenario.resource
Resource            ../../src/resource/data_driven/login/login_driven.resource
Test Teardown       TEARDOWN: CLOSE BROWSER
Metadata            Suite    Login
Metadata            Owner    AQuA-QE
Metadata            Version    1.1
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-036: Validate Login - tela interna sem sessão leva ao Keycloak e, após entrar, mostra Meu trabalho
    [Documentation]    Abrir /home sem sessão leva ao Keycloak; após o login, mostra Meu trabalho com o nome do usuário.
    [Tags]    UI    Login    PRD-RF-036    smoke
    [Setup]    PREREQUISITE: OPEN BROWSER ON PROTECTED PAGE
    LOGIN - PERSONA    ${login_pm_username}    ${login_pm_display_name}

CT-002 - PRD-RF-036: Validate Logout - sair volta à landing e a tela interna volta a pedir login
    [Documentation]    Sair encerra a sessão na aplicação e no Keycloak.
    [Tags]    UI    Logout    PRD-RF-036    security    ASVS-V3
    [Setup]    PREREQUISITE: OPEN BROWSER ON PROTECTED PAGE
    LOGIN - PERSONA - E SAIR    ${login_pm_username}    ${login_pm_display_name}

CT-003 - PRD-RF-036: Validate Landing - abre sem sessão e "Entrar no sistema" leva ao login
    [Documentation]    A landing é pública; o botão de entrada leva ao Keycloak.
    [Tags]    UI    Landing    PRD-RF-036    smoke
    [Setup]    PREREQUISITE: OPEN BROWSER ON LANDING
    LANDING - ENTRAR NO SISTEMA
