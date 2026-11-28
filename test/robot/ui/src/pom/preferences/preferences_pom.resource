*** Settings ***
Documentation       Ações na tela de Preferências (#15). Sem asserções.
Library             Browser
Resource            ../../resource/locators/preferences/preferences_locator.resource

*** Keywords ***
Click Profile Preferences
    Click    ${PREFERENCES_MENU_ITEM}

Wait For Preferences Page
    Wait For Elements State    ${PREFERENCES_NOTIFY_MENTION}    enabled    timeout=30s

Choose Theme
    [Arguments]    ${value}
    ${locator}=    Evaluate    $PREFERENCES_THEME_BY_VALUE.replace('{}', $value)
    Click    ${locator}

Toggle Mention Notification
    Click    ${PREFERENCES_NOTIFY_MENTION}

Wait For Preferences Saved
    Wait For Elements State    ${PREFERENCES_SAVED}    visible    timeout=30s
    Wait For Elements State    ${PREFERENCES_NOTIFY_MENTION}    enabled    timeout=30s

Get Document Theme
    ${theme}=    Get Attribute    ${PREFERENCES_DOCUMENT}    data-theme
    RETURN    ${theme}

Is Mention Notification On
    ${state}=    Get Checkbox State    ${PREFERENCES_NOTIFY_MENTION}
    RETURN    ${state}

Reload Preferences Page
    Reload
    Wait For Preferences Page
