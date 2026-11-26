*** Settings ***
Documentation       Gates (SSD 5; #78): o gate vale para todos os itens do fluxo, então o teste usa a transição
...                 Ideia → Cancelado da Iniciativa e sempre remove o gate no fim, para não travar as outras suítes.
Library             Collections
Resource            ../../../pom/governance/gates/gates_pom.resource
Resource            ../../workflow/transitions/transitions_flow.resource

*** Keywords ***
Gates - Bloquear Aprovar E Liberar
    [Documentation]    Só o administrador configura; o gate bloqueia com as regras não atendidas; a aprovação é de
    ...    quem tem o papel; com tudo atendido, a transição passa.
    [Arguments]    ${title}    ${gate_name}    ${to_key}
    ${init}=    Create Initiative For Transitions    ${title}
    ${transition}=    Transition Id To    pm    ${init}[key]    ${to_key}
    Set Test Variable    ${GATE_TRANSITION}    ${transition}
    ${size_rule}=    Create Dictionary    type=REQUIRED_FIELD    params=${{ {'field': 'size'} }}    message=Informe a estimativa.
    ${approval_rule}=    Create Dictionary    type=ROLE_APPROVAL    params=${{ {'role': 'TECH_LEAD', 'label': 'Architecture Gate'} }}
    ...    message=Aguardando arquitetura.
    ${rules}=    Create List    ${size_rule}    ${approval_rule}
    ${gate}=    Create Dictionary    name=${gate_name}    rules=${rules}
    ${denied}=    PUT Gate As Persona    pm    ${transition}    ${gate}
    Should Be Equal As Integers    ${denied.status_code}    403
    ${saved}=    PUT Gate As Persona    admin    ${transition}    ${gate}
    Should Be Equal As Integers    ${saved.status_code}    200
    ${move}=    Create Dictionary    transitionId=${transition}    version=${init}[version]
    ${blocked}=    POST Transition As Persona    pm    ${init}[key]    ${move}
    Should Be Equal As Integers    ${blocked.status_code}    422
    Should Be Equal    ${blocked.json()}[code]    GATE_BLOCKED
    ${failed}=    Evaluate    [r['type'] for r in $blocked.json()['failedRules']]
    ${expected}=    Create List    REQUIRED_FIELD    ROLE_APPROVAL
    Lists Should Be Equal    ${failed}    ${expected}
    ${patch}=    Create Dictionary    size=M    version=${init}[version]
    ${patched}=    PATCH Artifact As Persona    pm    ${init}[key]    ${patch}
    Should Be Equal As Integers    ${patched.status_code}    200
    ${panel}=    GET Gate Panel As Persona    tl    ${init}[key]
    Should Be Equal As Integers    ${panel.status_code}    200
    ${gate_panel}=    Evaluate    [g for g in $panel.json()['items'] if g['transitionId'] == $transition][0]
    Should Be Equal As Integers    ${gate_panel}[satisfied]    1
    ${rule_id}=    Set Variable    ${gate_panel}[rules][1][ruleId]
    Should Be True    ${gate_panel}[rules][1][canDecide]
    ${approval}=    Create Dictionary    gateRuleId=${rule_id}    decision=APPROVED    comment=Ok
    ${not_role}=    POST Approval As Persona    pm    ${init}[key]    ${approval}
    Validate Error Body    ${not_role}    403    APPROVER_ROLE_REQUIRED
    ${approved}=    POST Approval As Persona    tl    ${init}[key]    ${approval}
    Should Be Equal As Integers    ${approved.status_code}    200
    ${after}=    Evaluate    [g for g in $approved.json()['items'] if g['transitionId'] == $transition][0]
    Should Be Equal As Integers    ${after}[satisfied]    2
    ${move}=    Create Dictionary    transitionId=${transition}    version=${patched.json()}[version]
    ${moved}=    POST Transition As Persona    pm    ${init}[key]    ${move}
    Should Be Equal As Integers    ${moved.status_code}    200
    Should Be Equal    ${moved.json()}[status][key]    ${to_key}

Gates - Regra Ainda Indisponivel
    [Documentation]    BUSINESS_CASE_VALIDATED depende do business case, que ainda não existe: o gate é recusado.
    [Arguments]    ${title}    ${to_key}
    ${init}=    Create Initiative For Transitions    ${title}
    ${transition}=    Transition Id To    pm    ${init}[key]    ${to_key}
    ${rule}=    Create Dictionary    type=BUSINESS_CASE_VALIDATED    message=Valide o business case.
    ${rules}=    Create List    ${rule}
    ${gate}=    Create Dictionary    name=Business case    rules=${rules}
    ${refused}=    PUT Gate As Persona    admin    ${transition}    ${gate}
    Validate Error Body    ${refused}    400    VALIDATION_FAILED
    Should Contain    ${refused.text}    business case

Gates - Remover Gate Do Teste
    [Documentation]    Limpeza: remove o gate criado pelo teste, se existir (204 ou 404).
    ${transition}=    Get Variable Value    ${GATE_TRANSITION}    ${None}
    IF    $transition is not None
        ${response}=    DELETE Gate As Persona    admin    ${transition}
        Should Be True    ${response.status_code} in (204, 404)
    END
