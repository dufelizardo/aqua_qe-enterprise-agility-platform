*** Settings ***
Documentation       Ações na tela Admin › Gates e nos Próximos passos do artefato (#78). Sem asserções.
Library             Browser
Resource            ../../resource/locators/gates/gates_locator.resource

*** Keywords ***
Choose Gates Workflow For Type
    [Documentation]    Escolhe o fluxo cujo rótulo cita o tipo de artefato, como "(INITIATIVE)".
    [Arguments]    ${type}
    Wait For Elements State    ${GATES_WORKFLOW}    visible    timeout=30s
    Wait For Condition    Element Count    ${GATES_WORKFLOW} >> option    >    0    timeout=30s
    ${options}=    Get Select Options    ${GATES_WORKFLOW}
    ${value}=    Evaluate    [o['value'] for o in $options if '(' + $type + ')' in o['label']][0]
    Select Options By    ${GATES_WORKFLOW}    value    ${value}

Open Gate Of Transition
    [Arguments]    ${path}
    ${row}=    Evaluate    $GATES_TRANSITION_BY_PATH.replace('{}', $path)
    Click    ${row} >> [data-testid="gates-configure"]
    Wait For Elements State    ${GATES_NAME}    visible    timeout=10s

Fill Approval Gate
    [Documentation]    Nome do gate e uma regra de aprovação de Tech Lead com a mensagem.
    [Arguments]    ${name}    ${message}
    Fill Text    ${GATES_NAME}    ${name}
    Select Options By    ${GATES_RULE_TYPE}    value    ROLE_APPROVAL
    Fill Text    ${GATES_RULE_MESSAGE}    ${message}

Save Gate
    Click    ${GATES_SAVE}

Get Transition Row Text
    [Arguments]    ${path}
    ${row}=    Evaluate    $GATES_TRANSITION_BY_PATH.replace('{}', $path)
    ${text}=    Get Text    ${row}
    RETURN    ${text}

Remove Gate Accepting
    ${promise}=    Promise To    Wait For Alert    action=accept    timeout=10s
    Click    ${GATES_REMOVE}
    Wait For    ${promise}

Get Gate Step Text
    [Arguments]    ${to_key}
    ${step}=    Evaluate    $GATE_STEP.replace('{}', $to_key)
    Wait For Elements State    ${step}    visible    timeout=30s
    ${text}=    Get Text    ${step}
    RETURN    ${text}

Is Gate Move Enabled
    [Arguments]    ${to_key}
    ${step}=    Evaluate    $GATE_STEP.replace('{}', $to_key)
    ${state}=    Get Element States    ${step} >> [data-testid="gate-move"]
    ${enabled}=    Evaluate    'enabled' in $state
    RETURN    ${enabled}

Approve Gate Rule
    [Arguments]    ${to_key}
    ${step}=    Evaluate    $GATE_STEP.replace('{}', $to_key)
    Click    ${step} >> [data-testid="gate-approve"]
    Click    ${step} >> [data-testid="gate-confirm"]

Click Gate Move
    [Arguments]    ${to_key}
    ${step}=    Evaluate    $GATE_STEP.replace('{}', $to_key)
    Click    ${step} >> [data-testid="gate-move"]

Get Artifact Status Text
    ${text}=    Get Text    css=[data-testid="artifact-status"]
    RETURN    ${text}

Open Saved Page
    [Documentation]    Volta a um endereço guardado antes (a página do artefato).
    [Arguments]    ${url}
    Go To    ${url}
