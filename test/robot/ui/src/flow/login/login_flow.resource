*** Settings ***
Documentation       Landing, login e logout pelo navegador (BFF, ADR-16): escolhe o caminho e faz as asserções.
Resource            ../../pom/common/aquaqe_ui_common.resource
Resource            ../../pom/landing/landing_pom.resource
Resource            ../../pom/login/login_pom.resource
Resource            ../../pom/shell/shell_pom.resource

*** Keywords ***
Landing - Publica E Leva Ao Login
    [Documentation]    A landing abre sem sessão; "Entrar no sistema" leva ao login do Keycloak.
    Wait For Landing Page
    ${title}=    Get Landing Title Text
    Should Not Be Empty    ${title}
    Click Landing Enter
    Wait For Keycloak Login Page

Login - Sucesso
    [Documentation]    Na página de login do Keycloak, entra e volta para Meu trabalho com o nome do usuário no
    ...    cabeçalho e na saudação.
    [Arguments]    ${username}    ${display_name}
    Wait For Keycloak Login Page
    Fill Keycloak Credentials    ${username}    ${AQUA_TEST_PASSWORD}
    Submit Keycloak Login
    Wait For Home Page
    ${url}=    Get Current Page Url
    Should Start With    ${url}    ${AQUA_UI_URL}/home
    ${title}=    Get Page Title Text
    Should Be Equal    ${title}    Meu trabalho
    ${welcome}=    Get Welcome Text
    Should Contain    ${welcome}    ${display_name}
    Open Profile Menu
    ${profile}=    Get Profile Name Text
    Should Contain    ${profile}    ${display_name}

Logout - Volta A Landing E Exige Login
    [Documentation]    Sair encerra a sessão na aplicação e no Keycloak: o usuário volta à landing, e uma tela
    ...    interna volta a pedir login.
    Click Logout
    Wait For Landing Page
    Go To AQuA-QE Path    /home
    Wait For Keycloak Login Page
    ${url}=    Get Current Page Url
    Should Not Start With    ${url}    ${AQUA_UI_URL}
