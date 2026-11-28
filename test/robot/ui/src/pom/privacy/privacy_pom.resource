*** Settings ***
Documentation       Ações em Administração › Pessoas e privacidade (#32). Sem asserções.
Library             Browser
Library             OperatingSystem
Resource            ../../resource/locators/privacy/privacy_locator.resource

*** Keywords ***
Click People Tab
    Click    ${PEOPLE_TAB}

Search People
    [Arguments]    ${text}
    Fill Text    ${PEOPLE_SEARCH}    ${text}
    Click    ${PEOPLE_SEARCH_SUBMIT}
    Wait For Elements State    ${PEOPLE_FIRST_ROW}    visible    timeout=30s

Get First Person Name
    ${name}=    Get Attribute    ${PEOPLE_FIRST_ROW}    data-name
    RETURN    ${name}

Download First Person Data
    [Documentation]    Clica em "Baixar dados" e devolve o nome sugerido e o texto do JSON baixado.
    ${promise}=    Promise To Wait For Download
    Click    ${PEOPLE_FIRST_EXPORT}
    ${file}=    Wait For    ${promise}
    ${text}=    Get File    ${file}[saveAs]    encoding=UTF-8
    RETURN    ${file}[suggestedFilename]    ${text}

Open Anonymize Confirmation
    Click    ${PEOPLE_FIRST_ANONYMIZE}
    Wait For Elements State    ${PEOPLE_ANONYMIZE_FORM}    visible    timeout=30s

Type Anonymize Name
    [Arguments]    ${text}
    Fill Text    ${PEOPLE_ANONYMIZE_NAME}    ${text}

Is Anonymize Confirm Enabled
    ${states}=    Get Element States    ${PEOPLE_ANONYMIZE_CONFIRM}
    ${enabled}=    Evaluate    'enabled' in $states
    RETURN    ${enabled}

Cancel Anonymize With Escape
    Press Keys    ${PEOPLE_ANONYMIZE_NAME}    Escape
    Wait For Elements State    ${PEOPLE_ANONYMIZE_FORM}    detached    timeout=30s
