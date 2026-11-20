*** Settings ***
Documentation       Ações na tela de Portfolio. Sem asserções: as esperas só sincronizam com a página.
Library             Browser
Resource            ../../resource/locators/portfolio/portfolio_locator.resource

*** Keywords ***
Click Portfolio Nav Link
    Click    ${PORTFOLIO_NAV_LINK}

Get Create Board Button Count
    ${count}=    Get Element Count    ${PORTFOLIO_CREATE_BOARD}
    RETURN    ${count}

Click Create Board
    Click    ${PORTFOLIO_CREATE_BOARD}

Wait For Portfolio Board
    Wait For Elements State    ${PORTFOLIO_BOARD}    visible    timeout=30s

Fill New Initiative
    [Arguments]    ${title}    ${description}    ${unit_name}
    Click    ${PORTFOLIO_NEW_INITIATIVE}
    Fill Text    ${PORTFOLIO_NEW_TITLE}    ${title}
    Fill Text    ${PORTFOLIO_NEW_DESCRIPTION}    ${description}
    Select Options By    ${PORTFOLIO_NEW_UNIT}    label    ${unit_name}

Submit New Initiative
    Click    ${PORTFOLIO_NEW_SUBMIT}

Card Locator
    [Arguments]    ${status}    ${title}
    ${locator}=    Evaluate    $PORTFOLIO_CARD_IN_COLUMN.replace('{status}', $status).replace('{title}', $title)
    RETURN    ${locator}

Wait For Card In Column
    [Arguments]    ${status}    ${title}
    ${locator}=    Card Locator    ${status}    ${title}
    Wait For Elements State    ${locator}    visible    timeout=30s

Get Card Count In Column
    [Arguments]    ${status}    ${title}
    ${locator}=    Card Locator    ${status}    ${title}
    ${count}=    Get Element Count    ${locator}
    RETURN    ${count}

Move Card Using Menu
    [Arguments]    ${status}    ${title}    ${to_status}
    ${card}=    Card Locator    ${status}    ${title}
    Click    ${card} >> ${PORTFOLIO_CARD_MENU}
    ${item}=    Evaluate    $PORTFOLIO_MOVE_TO.replace('{status}', $to_status)
    Wait For Elements State    ${item}    enabled    timeout=30s
    Click    ${item}

Show List
    Click    ${PORTFOLIO_MODE_LIST}
    Wait For Elements State    ${PORTFOLIO_LIST}    visible    timeout=30s

Get List Text
    ${text}=    Get Text    ${PORTFOLIO_LIST}
    RETURN    ${text}

Open Board Configuration
    Click    ${PORTFOLIO_CONFIGURE_BOARD}
    Wait For Elements State    ${PORTFOLIO_CONFIG_FIRST_WIP}    visible    timeout=30s

Set First Column WIP Limit
    [Arguments]    ${limit}
    Fill Text    ${PORTFOLIO_CONFIG_FIRST_WIP}    ${limit}

Save Board Configuration
    Click    ${PORTFOLIO_CONFIG_SAVE}
    Wait For Elements State    ${PORTFOLIO_CONFIG_PANEL}    detached    timeout=30s

Get Idea Column Counter Text
    ${text}=    Get Text    ${PORTFOLIO_WIP_IDEA}
    RETURN    ${text}

Wait For Idea Column Counter
    [Arguments]    ${operator}    ${expected}
    Wait For Condition    Text    ${PORTFOLIO_WIP_IDEA}    ${operator}    ${expected}    timeout=30s

Block Card Using Menu
    [Arguments]    ${status}    ${title}    ${reason}
    ${card}=    Card Locator    ${status}    ${title}
    Click    ${card} >> ${PORTFOLIO_CARD_MENU}
    Click    ${PORTFOLIO_CARD_BLOCK}
    Fill Text    ${PORTFOLIO_BLOCK_REASON}    ${reason}
    Click    ${PORTFOLIO_BLOCK_CONFIRM}

Unblock Card Using Menu
    [Arguments]    ${status}    ${title}
    ${card}=    Card Locator    ${status}    ${title}
    Click    ${card} >> ${PORTFOLIO_CARD_MENU}
    Click    ${PORTFOLIO_CARD_UNBLOCK}

Wait For Card Blocked State
    [Arguments]    ${status}    ${title}    ${state}
    ${card}=    Card Locator    ${status}    ${title}
    Wait For Elements State    ${card} >> ${PORTFOLIO_CARD_BLOCKED_BADGE}    ${state}    timeout=30s

Click Tactical Nav Link
    Click    ${LEVEL_NAV_TACTICAL}

Wait For Level Board Page
    [Arguments]    ${title}
    Wait For Condition    Text    ${PORTFOLIO_PAGE_TITLE}    ==    ${title}    timeout=30s
    Wait For Elements State    css=[data-testid="board"], [data-testid="board-empty"]    visible
    ...    timeout=30s

Get Card Key
    [Arguments]    ${status}    ${title}
    ${card}=    Card Locator    ${status}    ${title}
    ${key}=    Get Text    ${card} >> css=.mono
    RETURN    ${key}

Fill New Child Item
    [Documentation]    Formulário de novo item com pai: tipo padrão do nível, título, pai (rótulo "CHAVE · título") e unidade.
    [Arguments]    ${title}    ${description}    ${parent_label}    ${unit_name}
    Click    ${PORTFOLIO_NEW_INITIATIVE}
    Fill Text    ${PORTFOLIO_NEW_TITLE}    ${title}
    Fill Text    ${PORTFOLIO_NEW_DESCRIPTION}    ${description}
    Wait For Elements State    ${LEVEL_NEW_PARENT} >> css=option >> text=${parent_label}    attached    timeout=30s
    Select Options By    ${LEVEL_NEW_PARENT}    label    ${parent_label}
    Select Options By    ${PORTFOLIO_NEW_UNIT}    label    ${unit_name}

Click Squad Nav Link
    Click    ${SQUAD_NAV}

Fill New Item Without Parent
    [Documentation]    Novo item escolhendo o tipo pelo rótulo, sem item pai, na unidade informada.
    [Arguments]    ${type_label}    ${title}    ${unit_name}
    Click    ${PORTFOLIO_NEW_INITIATIVE}
    Select Options By    ${LEVEL_NEW_TYPE}    label    ${type_label}
    Fill Text    ${PORTFOLIO_NEW_TITLE}    ${title}
    Select Options By    ${PORTFOLIO_NEW_UNIT}    label    ${unit_name}

Open Sprints Tab For Squad
    [Arguments]    ${squad_name}
    Click    ${SQUAD_TAB_SPRINTS}
    Wait For Elements State    ${SPRINTS_SQUAD}    visible    timeout=30s
    Select Options By    ${SPRINTS_SQUAD}    label    ${squad_name}

Create Sprint In Tab
    [Arguments]    ${name}
    Fill Text    ${SPRINT_NAME}    ${name}
    Click    ${SPRINT_CREATE}

Sprint Row
    [Arguments]    ${name}
    ${row}=    Evaluate    $SPRINT_ROW_BY_NAME.replace('{}', $name)
    RETURN    ${row}

Wait For Sprint State
    [Arguments]    ${name}    ${state_label}
    ${row}=    Sprint Row    ${name}
    Wait For Condition    Text    ${row} >> css=.badge    ==    ${state_label}    timeout=30s

Click Sprint Action
    [Arguments]    ${name}    ${testid}
    ${row}=    Sprint Row    ${name}
    Click    ${row} >> css=[data-testid="${testid}"]

Include Backlog Item In Sprint
    [Arguments]    ${title}    ${sprint_name}
    Click    ${SQUAD_TAB_BACKLOG}
    ${row}=    Evaluate    $BACKLOG_ROW_BY_TITLE.replace('{}', $title)
    Wait For Elements State    ${row} >> css=[data-testid="backlog-sprint"]    visible    timeout=30s
    Select Options By    ${row} >> css=[data-testid="backlog-sprint"]    label    ${sprint_name}

Get Close Confirmation Text
    Wait For Elements State    ${SPRINT_CLOSE_CONFIRM}    visible    timeout=30s
    ${text}=    Get Text    ${SPRINT_CLOSE_CONFIRM}
    RETURN    ${text}

Confirm Sprint Close
    Click    ${SPRINT_CLOSE_YES}
