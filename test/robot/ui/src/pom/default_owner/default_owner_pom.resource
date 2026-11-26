*** Settings ***
Documentation       Ações na tela Admin › Responsável padrão e no campo Owner do novo item (#43). Sem asserções.
Library             Browser
Resource            ../../resource/locators/default_owner/default_owner_locator.resource

*** Keywords ***
Choose Default Owner Unit
    [Documentation]    Escolhe a unidade cujo rótulo contém o nome e espera as regras dela.
    [Arguments]    ${name}
    Wait For Condition    Element Count    ${OWNER_UNIT} >> option    >    0    timeout=30s
    ${options}=    Get Select Options    ${OWNER_UNIT}
    ${value}=    Evaluate    [o['value'] for o in $options if $name in o['label']][0]
    Select Options By    ${OWNER_UNIT}    value    ${value}

Set Default Owner Mode
    [Documentation]    Abre a regra do tipo, escolhe o modo (NONE, CREATOR ou ROLE) e salva.
    [Arguments]    ${type}    ${mode}
    ${edit}=    Evaluate    $OWNER_EDIT.replace('{}', $type)
    Click    ${edit}
    Select Options By    ${OWNER_MODE}    value    ${mode}
    Click    ${OWNER_SAVE}

Click Default Owner Inherit
    [Arguments]    ${type}
    ${button}=    Evaluate    $OWNER_INHERIT.replace('{}', $type)
    Click    ${button}

Wait For Default Owner Summary
    [Arguments]    ${type}    ${text}
    ${summary}=    Evaluate    $OWNER_SUMMARY.replace('{}', $type)
    Wait For Condition    Text    ${summary}    ==    ${text}    timeout=15s

Get Default Owner Row Text
    [Arguments]    ${type}
    ${row}=    Evaluate    $OWNER_ROW.replace('{}', $type)
    ${text}=    Get Text    ${row}
    RETURN    ${text}

Wait For New Item Owner
    Wait For Elements State    ${NEW_ITEM_OWNER}    visible    timeout=15s

Get New Item Owner Label
    ${labels}=    Get Selected Options    ${NEW_ITEM_OWNER}    label
    RETURN    ${labels}

Get New Item Owner Hint
    ${text}=    Get Text    ${NEW_ITEM_OWNER_HINT}
    RETURN    ${text}
