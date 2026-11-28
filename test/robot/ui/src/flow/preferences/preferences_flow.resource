*** Settings ***
Documentation       Preferências do usuário (#15; REV-10; UX/UI 7.14): escolhe o caminho e faz as asserções.
Resource            ../../pom/common/aquaqe_ui_common.resource
Resource            ../../pom/navigation/navigation_pom.resource
Resource            ../../pom/preferences/preferences_pom.resource
Resource            ../login/login_flow.resource

*** Keywords ***
Preferencias - Abrir Pelo Menu
    [Documentation]    Entra e abre Preferências pelo menu do avatar.
    [Arguments]    ${username}    ${display_name}
    Login - Sucesso    ${username}    ${display_name}
    Click Profile Preferences
    Wait For Url To End With    /preferencias
    Wait For Preferences Page

Preferencias - Tema Escuro E Mencao Desligada
    [Documentation]    O tema escuro vale na hora e depois de recarregar; a menção desligada também fica gravada.
    ...    No fim, volta ao tema do sistema e liga a menção.
    Choose Theme    DARK
    Wait For Preferences Saved
    ${theme}=    Get Document Theme
    Should Be Equal    ${theme}    dark
    Toggle Mention Notification
    Wait For Preferences Saved
    Reload Preferences Page
    ${theme}=    Get Document Theme
    Should Be Equal    ${theme}    dark
    ${mention}=    Is Mention Notification On
    Should Not Be True    ${mention}
    Choose Theme    LIGHT
    Wait For Preferences Saved
    ${theme}=    Get Document Theme
    Should Be Equal    ${theme}    light
    Preferencias - Voltar Ao Padrao

Preferencias - Voltar Ao Padrao
    [Documentation]    Tema do sistema e menção ligada, para não afetar as outras suítes.
    Choose Theme    SYSTEM
    Wait For Preferences Saved
    ${mention}=    Is Mention Notification On
    IF    not $mention
        Toggle Mention Notification
        Wait For Preferences Saved
    END
