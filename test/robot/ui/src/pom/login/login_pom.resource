*** Settings ***
Documentation       Ações na página de login do Keycloak. Sem asserções: as esperas só sincronizam com a página.
Library             Browser
Resource            ../../resource/locators/login/login_locator.resource

*** Keywords ***
Wait For Keycloak Login Page
    Wait For Elements State    ${LOGIN_USERNAME_INPUT}    visible    timeout=30s

Fill Keycloak Credentials
    [Arguments]    ${username}    ${password}
    Fill Text    ${LOGIN_USERNAME_INPUT}    ${username}
    Fill Secret    ${LOGIN_PASSWORD_INPUT}    $password

Submit Keycloak Login
    Click    ${LOGIN_SUBMIT_BUTTON}
