*** Settings ***
Documentation       Ações na tela Wiki. Sem asserções: as esperas só sincronizam.
Library             Browser
Library             Collections
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
    [Documentation]    Editar, clicar na área de texto, ligar o negrito pela barra, digitar e salvar (bug #67).
    [Arguments]    ${text}    ${summary}
    Click    ${WIKI_EDIT}
    Wait For Elements State    ${WIKI_EDITOR}    visible    timeout=30s
    Click    ${WIKI_EDITOR}
    Click    ${WIKI_RT_BOLD}
    Keyboard Input    type    ${text}
    Fill Text    ${WIKI_SUMMARY}    ${summary}
    Click    ${WIKI_SAVE}

Get Bold Text Count
    ${count}=    Get Element Count    ${WIKI_READ_BOLD}
    RETURN    ${count}

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

Click Wiki Up
    Click    ${WIKI_UP}

Move Wiki Page Into
    [Documentation]    "Mover para…" escolhendo uma página da raiz pelo título (na raiz, o rótulo é o próprio título;
    ...    em níveis abaixo ele vem precedido de "— ").
    [Arguments]    ${target_title}
    Click    ${WIKI_MOVE}
    Select Options By    ${WIKI_MOVE_TARGET}    label    ${target_title}
    Click    ${WIKI_MOVE_CONFIRM}

Get Wiki Tree Titles
    ${elements}=    Get Elements    ${WIKI_TREE_TITLES}
    ${titles}=    Create List
    FOR    ${element}    IN    @{elements}
        ${text}=    Get Text    ${element}
        Append To List    ${titles}    ${text}
    END
    RETURN    ${titles}

Get Wiki Tree Level
    [Arguments]    ${title}
    ${locator}=    Evaluate    $WIKI_TREE_ITEM_BY_TITLE.replace('{}', $title)
    ${level}=    Get Attribute    ${locator}    aria-level
    RETURN    ${level}

Wait For Wiki Tree Level
    [Arguments]    ${title}    ${level}
    ${locator}=    Evaluate    $WIKI_TREE_ITEM_BY_TITLE.replace('{}', $title)
    Wait For Condition    Attribute    ${locator}    aria-level    ==    ${level}    timeout=30s
