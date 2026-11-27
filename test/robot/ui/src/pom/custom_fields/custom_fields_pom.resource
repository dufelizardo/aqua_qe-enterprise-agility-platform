*** Settings ***
Documentation       Ações dos campos customizados (#17). Sem asserções: as esperas só sincronizam.
Library             Browser
Resource            ../../resource/locators/custom_fields/custom_fields_locator.resource

*** Keywords ***
Choose Custom Fields Type
    [Arguments]    ${type_key}
    Wait For Elements State    ${CUSTOM_FIELDS_TYPE}    visible    timeout=30s
    Select Options By    ${CUSTOM_FIELDS_TYPE}    value    ${type_key}

Add Select Custom Field
    [Documentation]    Preenche rótulo, chave, tipo "Lista de opções" e as opções (uma por linha) e adiciona.
    [Arguments]    ${label}    ${key}    @{options}
    Fill Text    ${CUSTOM_FIELDS_NEW_LABEL}    ${label}
    Fill Text    ${CUSTOM_FIELDS_NEW_KEY}    ${key}
    Select Options By    ${CUSTOM_FIELDS_NEW_TYPE}    value    SELECT
    ${text}=    Evaluate    '\\n'.join($options)
    Fill Text    ${CUSTOM_FIELDS_NEW_OPTIONS}    ${text}
    Click    ${CUSTOM_FIELDS_ADD}

Custom Field Row Locator
    [Arguments]    ${key}
    ${locator}=    Evaluate    $CUSTOM_FIELDS_ROW_BY_KEY.replace('{}', $key)
    RETURN    ${locator}

Wait For Custom Field Row
    [Arguments]    ${key}
    ${row}=    Custom Field Row Locator    ${key}
    Wait For Elements State    ${row}    visible    timeout=30s

Remove Custom Field
    [Arguments]    ${key}
    ${row}=    Custom Field Row Locator    ${key}
    Click    ${row} >> ${CUSTOM_FIELDS_REMOVE}
    Click    ${CUSTOM_FIELDS_REMOVE_OK}
    Wait For Elements State    ${row}    detached    timeout=30s

Get Custom Field Row Count
    [Arguments]    ${key}
    ${row}=    Custom Field Row Locator    ${key}
    ${count}=    Get Element Count    ${row}
    RETURN    ${count}

Choose Artifact Custom Option
    [Documentation]    Na página do artefato, clica no campo e escolhe a opção da lista.
    [Arguments]    ${key}    ${option}
    ${locator}=    Evaluate    $ARTIFACT_CUSTOM_FIELD_BY_KEY.replace('{}', $key)
    Wait For Elements State    ${locator}    visible    timeout=30s
    Click    ${locator}
    Select Options By    ${ARTIFACT_CUSTOM_SELECT}    value    ${option}

Get Artifact Custom Field Text
    [Arguments]    ${key}
    ${locator}=    Evaluate    $ARTIFACT_CUSTOM_FIELD_BY_KEY.replace('{}', $key)
    Wait For Elements State    ${locator}    visible    timeout=30s
    ${text}=    Get Text    ${locator}
    RETURN    ${text}
