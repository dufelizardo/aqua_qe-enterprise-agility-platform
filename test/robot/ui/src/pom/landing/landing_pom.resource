*** Settings ***
Documentation       Ações na landing pública. Sem asserções.
Library             Browser
Resource            ../../resource/locators/landing/landing_locator.resource

*** Keywords ***
Wait For Landing Page
    Wait For Elements State    ${LANDING_TITLE}    visible    timeout=30s

Get Landing Title Text
    ${text}=    Get Text    ${LANDING_TITLE}
    RETURN    ${text}

Click Landing Enter
    Click    ${LANDING_ENTER_BUTTON}
