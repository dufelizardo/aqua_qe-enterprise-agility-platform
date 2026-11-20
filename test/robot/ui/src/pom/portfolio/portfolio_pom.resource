*** Settings ***
Documentation       Ações na tela de Portfolio. Sem asserções: as esperas só sincronizam com a página.
Library             Browser
Resource            ../../resource/locators/portfolio/portfolio_locator.resource

*** Keywords ***
Click Portfolio Nav Link
    Click    ${PORTFOLIO_NAV_LINK}

Wait For Portfolio Page
    Wait For Condition    Text    ${PORTFOLIO_PAGE_TITLE}    ==    Portfolio    timeout=30s
    Wait For Elements State    css=[data-testid="portfolio-board"], [data-testid="portfolio-empty"]    visible
    ...    timeout=30s

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
