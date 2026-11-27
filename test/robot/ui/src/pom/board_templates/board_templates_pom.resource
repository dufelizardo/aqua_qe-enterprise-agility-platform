*** Settings ***
Documentation       Ações dos templates de quadro (#50). Sem asserções: as esperas só sincronizam.
Library             Browser
Resource            ../../resource/locators/board_templates/board_templates_locator.resource

*** Keywords ***
Choose Board Template
    [Arguments]    ${template_name}
    Wait For Elements State    ${BOARD_TEMPLATE_SELECT}    visible    timeout=30s
    Select Options By    ${BOARD_TEMPLATE_SELECT}    label    ${template_name}
    Wait For Elements State    ${BOARD_TEMPLATE_SUMMARY}    visible    timeout=30s

Get Board Column Title
    [Arguments]    ${status_key}
    ${locator}=    Evaluate    $BOARD_COLUMN_TITLE.replace('{}', $status_key)
    Wait For Elements State    ${locator}    visible    timeout=30s
    ${text}=    Get Text    ${locator}
    RETURN    ${text}

Save Board As Template
    [Arguments]    ${template_name}
    Click    ${BOARD_SAVE_AS_TEMPLATE}
    Fill Text    ${BOARD_TEMPLATE_NAME}    ${template_name}
    Click    ${BOARD_TEMPLATE_SAVE}
    Wait For Elements State    ${BOARD_TEMPLATE_SAVED}    visible    timeout=30s

Get Board Template Saved Text
    ${text}=    Get Text    ${BOARD_TEMPLATE_SAVED}
    RETURN    ${text}

Board Template Row Locator
    [Arguments]    ${template_name}
    ${locator}=    Evaluate    $BOARD_TEMPLATES_ROW_BY_NAME.replace('{}', $template_name)
    RETURN    ${locator}

Wait For Board Template Row
    [Arguments]    ${template_name}
    ${row}=    Board Template Row Locator    ${template_name}
    Wait For Elements State    ${row}    visible    timeout=30s

Remove Board Template
    [Arguments]    ${template_name}
    ${row}=    Board Template Row Locator    ${template_name}
    Click    ${row} >> ${BOARD_TEMPLATES_REMOVE}
    Click    ${BOARD_TEMPLATES_REMOVE_OK}
    Wait For Elements State    ${row}    detached    timeout=30s

Get Board Template Row Count
    [Arguments]    ${template_name}
    ${row}=    Board Template Row Locator    ${template_name}
    ${count}=    Get Element Count    ${row}
    RETURN    ${count}
