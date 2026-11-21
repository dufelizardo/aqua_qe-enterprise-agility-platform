*** Settings ***
Documentation       Ações no menu lateral e na árvore organizacional. Sem asserções: as esperas só sincronizam.
Library             Browser
Resource            ../../resource/locators/navigation/navigation_locator.resource

*** Keywords ***
Org Node Locator
    [Arguments]    ${name}
    ${locator}=    Evaluate    $NAV_ORG_NODE_BY_NAME.replace('{}', $name)
    RETURN    ${locator}

Wait For Nav Org Tree
    Wait For Elements State    ${NAV_ORG_TREE}    visible    timeout=30s

Search Nav Org Tree
    [Arguments]    ${term}
    Fill Text    ${NAV_ORG_TREE_SEARCH}    ${term}

Clear Nav Org Tree Search
    Fill Text    ${NAV_ORG_TREE_SEARCH}    ${EMPTY}

Wait For Org Node
    [Arguments]    ${name}
    ${locator}=    Org Node Locator    ${name}
    Wait For Elements State    ${locator}    visible    timeout=30s

Wait For Org Node Hidden
    [Arguments]    ${name}
    ${locator}=    Org Node Locator    ${name}
    Wait For Elements State    ${locator}    detached    timeout=30s

Get Org Node Count
    [Arguments]    ${name}
    ${locator}=    Org Node Locator    ${name}
    ${count}=    Get Element Count    ${locator}
    RETURN    ${count}

Click Org Node
    [Arguments]    ${name}
    ${locator}=    Org Node Locator    ${name}
    Click    ${locator}

Click Org Node Toggle
    [Arguments]    ${name}
    ${locator}=    Evaluate    $NAV_ORG_TOGGLE_BY_NAME.replace('{}', $name)
    Click    ${locator}

Wait For Current Workspace
    [Arguments]    ${name}
    Wait For Condition    Text    ${NAV_WORKSPACE_CURRENT}    ==    ${name}    timeout=30s

Get Current Workspace Text
    ${text}=    Get Text    ${NAV_WORKSPACE_CURRENT}
    RETURN    ${text}

Get Sidebar Visible
    ${visible}=    Run Keyword And Return Status
    ...    Wait For Elements State    ${NAV_SIDEBAR}    visible    timeout=2s
    RETURN    ${visible}

Wait For Sidebar Hidden
    Wait For Elements State    ${NAV_SIDEBAR}    hidden    timeout=30s

Click Menu Toggle
    Click    ${NAV_MENU_TOGGLE}

Wait For Url To End With
    [Arguments]    ${suffix}
    Wait For Condition    Url    $=    ${suffix}    timeout=30s

Take Navigation Screenshot
    [Arguments]    ${name}
    Take Screenshot    filename=${name}
