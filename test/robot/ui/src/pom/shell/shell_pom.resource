*** Settings ***
Documentation       Ações na estrutura da aplicação (cabeçalho e home). Sem asserções.
Library             Browser
Resource            ../../resource/locators/shell/shell_locator.resource

*** Keywords ***
Wait For Home Page
    Wait For Elements State    ${SHELL_WELCOME}    visible    timeout=30s

Get Page Title Text
    ${text}=    Get Text    ${SHELL_PAGE_TITLE}
    RETURN    ${text}

Get Welcome Text
    ${text}=    Get Text    ${SHELL_WELCOME}
    RETURN    ${text}

Open Profile Menu
    Click    ${SHELL_PROFILE_BUTTON}

Get Profile Name Text
    ${text}=    Get Text    ${SHELL_PROFILE_NAME}
    RETURN    ${text}

Click Logout
    Click    ${SHELL_LOGOUT}

Go Home By Brand
    Click    ${SHELL_BRAND}

Get Home Recent Text
    ${text}=    Get Text    ${HOME_RECENT}
    RETURN    ${text}

Click Home Recent Item
    [Arguments]    ${text}
    ${locator}=    Evaluate    $HOME_RECENT_ITEM_BY_TEXT.replace('{}', $text)
    Click    ${locator}

Get Home News Text
    ${text}=    Get Text    ${HOME_NEWS}
    RETURN    ${text}
