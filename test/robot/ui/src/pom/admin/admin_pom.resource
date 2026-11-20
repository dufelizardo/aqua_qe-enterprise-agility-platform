*** Settings ***
Documentation       Ações na Administração › Estrutura organizacional. Sem asserções: as esperas só sincronizam.
Library             Browser
Resource            ../../resource/locators/admin/admin_locator.resource

*** Keywords ***
Close Open Menu
    Keyboard Key    press    Escape

Get Admin Nav Link Count
    ${count}=    Get Element Count    ${ADMIN_NAV_LINK}
    RETURN    ${count}

Click Admin Nav Link
    Click    ${ADMIN_NAV_LINK}

Wait For Organization Tree
    Wait For Elements State    ${ADMIN_ORG_TREE}    visible    timeout=30s

Tree Item Locator
    [Arguments]    ${name}
    ${locator}=    Evaluate    $ADMIN_ORG_TREE_ITEM_BY_NAME.replace('{}', $name)
    RETURN    ${locator}

Select Tree Unit
    [Arguments]    ${name}
    ${locator}=    Tree Item Locator    ${name}
    Click    ${locator}
    Wait For Detail Name    ${name}

Wait For Tree Unit
    [Arguments]    ${name}
    ${locator}=    Tree Item Locator    ${name}
    Wait For Elements State    ${locator}    visible    timeout=30s

Wait For Detail Name
    [Arguments]    ${name}
    Wait For Condition    Text    ${ADMIN_ORG_DETAIL_NAME}    ==    ${name}    timeout=30s

Get Detail Name Text
    ${text}=    Get Text    ${ADMIN_ORG_DETAIL_NAME}
    RETURN    ${text}

Create Child Unit
    [Arguments]    ${name}    ${level_label}
    Fill Text    ${ADMIN_ORG_NEW_NAME}    ${name}
    Select Options By    ${ADMIN_ORG_NEW_LEVEL}    label    ${level_label}
    Click    ${ADMIN_ORG_NEW_SUBMIT}

Rename Selected Unit
    [Arguments]    ${name}
    Fill Text    ${ADMIN_ORG_RENAME_INPUT}    ${name}
    Click    ${ADMIN_ORG_RENAME_SUBMIT}

Link Selected Unit To
    [Arguments]    ${target_label}
    Select Options By    ${ADMIN_ORG_LINK_TARGET}    label    ${target_label}
    Click    ${ADMIN_ORG_LINK_SUBMIT}

Wait For Serves List To Contain
    [Arguments]    ${name}
    Wait For Condition    Text    ${ADMIN_ORG_SERVES_LIST}    contains    ${name}    timeout=30s

Get Serves List Text
    ${text}=    Get Text    ${ADMIN_ORG_SERVES_LIST}
    RETURN    ${text}
