*** Settings ***
Resource            ../../flow/login/login_flow.resource
Resource            ../../flow/common/browser_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER ON LANDING
    [Documentation]    Navegador novo, sem cookies, na landing pública.
    Navegador - Abrir    /

PREREQUISITE: OPEN BROWSER ON PROTECTED PAGE
    [Documentation]    Navegador novo, sem cookies, direto numa tela interna (/home).
    Navegador - Abrir    /home

TEARDOWN: CLOSE BROWSER
    Navegador - Fechar

LANDING - ENTRAR NO SISTEMA
    [Documentation]    Jornada da landing até a página de login.
    Landing - Publica E Leva Ao Login

LOGIN - PERSONA
    [Documentation]    Jornada de login de uma persona.
    [Arguments]    ${username}    ${display_name}
    Login - Sucesso    ${username}    ${display_name}

LOGIN - PERSONA - E SAIR
    [Documentation]    Jornada de login seguida de logout.
    [Arguments]    ${username}    ${display_name}
    Login - Sucesso    ${username}    ${display_name}
    Logout - Volta A Landing E Exige Login
