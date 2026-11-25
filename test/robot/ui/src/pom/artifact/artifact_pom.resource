*** Settings ***
Documentation       Ações na página do artefato (#63). Sem asserções: as esperas só sincronizam.
Library             Browser
Resource            ../../resource/locators/artifact/artifact_locator.resource

*** Keywords ***
Open Card Page
    [Documentation]    Clica no título do cartão (já localizado) e abre a página do artefato.
    [Arguments]    ${card}
    Click    ${card} >> ${CARD_OPEN_LINK}

Wait For Artifact Title
    [Arguments]    ${title}
    Wait For Condition    Text    ${ARTIFACT_TITLE}    ==    ${title}    timeout=30s

Click Artifact Tab
    [Arguments]    ${type}
    ${locator}=    Evaluate    $ARTIFACT_TAB_BY_TYPE.replace('{}', $type)
    Click    ${locator}

Click Artifact Overview Tab
    Click    ${ARTIFACT_TAB_OVERVIEW}

Wait For Document Title
    [Arguments]    ${name}
    Wait For Condition    Text    ${DOC_TITLE}    ==    ${name}    timeout=30s

Click Document Edit
    Click    ${DOC_EDIT}

Write Document Section
    [Documentation]    Clica na área de texto da seção e digita.
    [Arguments]    ${key}    ${text}
    ${locator}=    Evaluate    $DOC_SECTION_EDITOR.replace('{}', $key)
    Wait For Elements State    ${locator}    visible    timeout=30s
    Click    ${locator}
    Keyboard Input    type    ${text}

Click Document Save
    Click    ${DOC_SAVE}

Click Document Approve
    Click    ${DOC_APPROVE}

Wait For Document Status
    [Arguments]    ${label}
    Wait For Condition    Text    ${DOC_STATUS}    ==    ${label}    timeout=30s

Wait For Document Version
    [Arguments]    ${number}
    Wait For Condition    Text    ${DOC_VERSION}    ==    Versão ${number}    timeout=30s

Wait For Overview Document Status
    [Arguments]    ${type}    ${label}
    ${locator}=    Evaluate    $ARTIFACT_DOC_STATUS_BY_TYPE.replace('{}', $type)
    Wait For Condition    Text    ${locator}    ==    ${label}    timeout=30s

Rename Artifact
    [Documentation]    Clica no título, troca o texto e salva.
    [Arguments]    ${new_title}
    Click    ${ARTIFACT_TITLE}
    Fill Text    ${ARTIFACT_TITLE_INPUT}    ${new_title}
    Click    ${ARTIFACT_TITLE_SAVE}

Choose Artifact Person
    [Documentation]    Clica no campo de pessoa (ownerId, technicalLeadId ou qaOwnerId), busca pelo nome e escolhe.
    [Arguments]    ${field}    ${name}
    ${locator}=    Evaluate    $ARTIFACT_FIELD_BY_NAME.replace('{}', $field)
    Click    ${locator}
    Fill Text    ${ARTIFACT_PERSON_SEARCH}    ${name}
    ${option}=    Evaluate    $ARTIFACT_PERSON_OPTION.replace('{}', $name)
    Click    ${option} >> nth=0

Wait For Artifact Field
    [Arguments]    ${field}    ${text}
    ${locator}=    Evaluate    $ARTIFACT_FIELD_BY_NAME.replace('{}', $field)
    Wait For Condition    Text    ${locator}    ==    ${text}    timeout=30s

Click Artifact Back
    Click    ${ARTIFACT_BACK}
