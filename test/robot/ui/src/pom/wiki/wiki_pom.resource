*** Settings ***
Documentation       Ações na tela Wiki. Sem asserções: as esperas só sincronizam.
Library             Browser
Resource            ../../resource/locators/wiki/wiki_locator.resource

*** Keywords ***
Wait For Wiki Tree
    Wait For Elements State    ${WIKI_TREE}    visible    timeout=30s

Create Wiki Page
    [Arguments]    ${title}
    Click    ${WIKI_NEW_PAGE}
    Fill Text    ${WIKI_NEW_TITLE}    ${title}
    Click    ${WIKI_CREATE_SUBMIT}

Wait For Regimento Empty
    Wait For Elements State    ${REGIMENTO_EMPTY}    visible    timeout=30s

Create Regimento
    [Documentation]    Na tela Regimento da unidade sem regimento: "Criar regimento" com o título informado.
    [Arguments]    ${title}
    Click    ${REGIMENTO_CREATE}
    Fill Text    ${WIKI_NEW_TITLE}    ${title}
    Click    ${WIKI_CREATE_SUBMIT}

Wait For Wiki Page Title
    [Arguments]    ${title}
    Wait For Condition    Text    ${WIKI_PAGE_TITLE}    ==    ${title}    timeout=30s

Wait For Wiki Page Link
    [Arguments]    ${title}
    ${locator}=    Evaluate    $WIKI_PAGE_LINK_BY_TITLE.replace('{}', $title)
    Wait For Elements State    ${locator}    visible    timeout=30s

Write Paragraph And Save
    [Arguments]    ${text}    ${summary}
    Click    ${WIKI_EDIT}
    Click    ${WIKI_ADD_PARAGRAPH}
    Fill Text    ${WIKI_LAST_BLOCK_INPUT}    ${text}
    Fill Text    ${WIKI_SUMMARY}    ${summary}
    Click    ${WIKI_SAVE}

Wait For Wiki Page Version
    [Arguments]    ${number}
    Wait For Condition    Text    ${WIKI_PAGE_VERSION}    ==    Versão ${number}    timeout=30s

Get Wiki Page Text
    ${text}=    Get Text    ${WIKI_PAGE}
    RETURN    ${text}

Open Wiki History
    Click    ${WIKI_HISTORY}
    Wait For Elements State    ${WIKI_VERSION_LINKS} >> nth=0    visible    timeout=30s

Get Wiki Version Count
    ${count}=    Get Element Count    ${WIKI_VERSION_LINKS}
    RETURN    ${count}

Wait For Wiki Status
    [Arguments]    ${label}
    Wait For Condition    Text    ${WIKI_PAGE_STATUS}    ==    ${label}    timeout=30s

Click Submit For Review
    Click    ${WIKI_SUBMIT_REVIEW}

Click Approve
    Click    ${WIKI_APPROVE}

Click Back To Draft
    Click    ${WIKI_BACK_DRAFT}

Get Approve Button Count
    ${count}=    Get Element Count    ${WIKI_APPROVE}
    RETURN    ${count}

Wait For Wiki Error
    Wait For Elements State    ${WIKI_ERROR}    visible    timeout=30s

Get Wiki Error Text
    ${text}=    Get Text    ${WIKI_ERROR}
    RETURN    ${text}

Get Wiki Missing Text
    ${text}=    Get Text    ${WIKI_MISSING}
    RETURN    ${text}

Take Wiki Screenshot
    [Arguments]    ${name}
    Take Screenshot    filename=${name}
