*** Settings ***
Documentation       Ações da seção Anexos da página do artefato (#13). Sem asserções: as esperas só sincronizam.
Library             Browser
Library             OperatingSystem
Resource            ../../resource/locators/attachments/attachments_locator.resource

*** Keywords ***
Wait For Attachments Section
    Wait For Elements State    ${ATTACHMENTS_SECTION}    visible    timeout=30s

Upload Attachment With Text
    [Documentation]    Grava um arquivo de texto na pasta de saída e o escolhe no campo de envio, que envia sozinho.
    [Arguments]    ${file_name}    ${content}
    ${path}=    Set Variable    ${OUTPUT DIR}${/}${file_name}
    Create File    ${path}    ${content}
    Upload File By Selector    ${ATTACHMENTS_INPUT}    ${path}

Attachment Row Locator
    [Arguments]    ${name}
    ${locator}=    Evaluate    $ATTACHMENTS_ROW_BY_NAME.replace('{}', $name)
    RETURN    ${locator}

Wait For Attachment Row
    [Arguments]    ${name}
    ${row}=    Attachment Row Locator    ${name}
    Wait For Elements State    ${row}    visible    timeout=30s

Get Attachment Download Href
    [Arguments]    ${name}
    ${row}=    Attachment Row Locator    ${name}
    ${href}=    Get Attribute    ${row} >> ${ATTACHMENTS_DOWNLOAD}    href
    RETURN    ${href}

Remove Attachment
    [Arguments]    ${name}
    ${row}=    Attachment Row Locator    ${name}
    Click    ${row} >> ${ATTACHMENTS_REMOVE}
    Click    ${row} >> ${ATTACHMENTS_REMOVE_OK}
    Wait For Elements State    ${row}    detached    timeout=30s

Wait For No Attachments
    Wait For Elements State    ${ATTACHMENTS_EMPTY}    visible    timeout=30s
