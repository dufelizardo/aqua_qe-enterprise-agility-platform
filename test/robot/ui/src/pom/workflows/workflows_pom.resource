*** Settings ***
Documentation       Ações na Administração › Workflows (#49). Sem asserções: as esperas só sincronizam.
Library             Browser
Resource            ../../resource/locators/workflows/workflows_locator.resource

*** Keywords ***
Click Workflows Tab
    Click    ${WORKFLOWS_TAB}

Wait For Workflows Page
    Wait For Elements State    ${WORKFLOWS_UNIT_SELECT}    visible    timeout=30s

Workflow Row Locator
    [Arguments]    ${name}
    ${locator}=    Evaluate    $WORKFLOWS_ROW_BY_NAME.replace('{}', $name)
    RETURN    ${locator}

Choose Workflows Unit
    [Arguments]    ${unit_name}
    ${option}=    Evaluate    $WORKFLOWS_UNIT_OPTION_BY_NAME.replace('{}', $unit_name)
    Wait For Elements State    ${option}    attached    timeout=30s
    ${value}=    Get Attribute    ${option}    value
    Select Options By    ${WORKFLOWS_UNIT_SELECT}    value    ${value}

Wait For Workflow Origin
    [Arguments]    ${name}    ${text}
    ${row}=    Workflow Row Locator    ${name}
    Wait For Condition    Text    ${row} >> ${WORKFLOWS_ROW_ORIGIN}    contains    ${text}    timeout=30s

Get Workflow Origin Text
    [Arguments]    ${name}
    ${row}=    Workflow Row Locator    ${name}
    ${text}=    Get Text    ${row} >> ${WORKFLOWS_ROW_ORIGIN}
    RETURN    ${text}

Wait For Workflow Version
    [Arguments]    ${name}    ${version}
    ${row}=    Workflow Row Locator    ${name}
    Wait For Condition    Text    ${row} >> ${WORKFLOWS_ROW_VERSION}    ==    ${version}    timeout=30s

Get Workflow Version Text
    [Arguments]    ${name}
    ${row}=    Workflow Row Locator    ${name}
    ${text}=    Get Text    ${row} >> ${WORKFLOWS_ROW_VERSION}
    RETURN    ${text}

Click Customize Workflow
    [Arguments]    ${name}
    ${row}=    Workflow Row Locator    ${name}
    Click    ${row} >> ${WORKFLOWS_CUSTOMIZE}

Wait For Workflow Editor
    Wait For Elements State    ${WORKFLOWS_EDITOR}    visible    timeout=30s

Add Workflow Status
    [Arguments]    ${status_name}    ${status_key}
    Fill Text    ${WORKFLOWS_NEW_STATUS_NAME}    ${status_name}
    Click    ${WORKFLOWS_ADD_STATUS}
    ${row}=    Evaluate    $WORKFLOWS_STATUS_ROW_BY_KEY.replace('{}', $status_key)
    Wait For Elements State    ${row}    visible    timeout=30s

Check Workflow Transition
    [Arguments]    ${from_key}    ${to_key}
    ${box}=    Evaluate    $WORKFLOWS_TRANSITION.replace('{}', $from_key + '-' + $to_key)
    Check Checkbox    ${box}

Publish Workflow Version
    Click    ${WORKFLOWS_PUBLISH}
    Wait For Elements State    ${WORKFLOWS_PUBLISH_OK}    visible    timeout=30s
    Click    ${WORKFLOWS_PUBLISH_OK}

Revert Workflow
    [Arguments]    ${name}
    ${row}=    Workflow Row Locator    ${name}
    Click    ${row} >> ${WORKFLOWS_REVERT}
    Wait For Elements State    ${WORKFLOWS_REVERT_OK}    visible    timeout=30s
    Click    ${WORKFLOWS_REVERT_OK}
