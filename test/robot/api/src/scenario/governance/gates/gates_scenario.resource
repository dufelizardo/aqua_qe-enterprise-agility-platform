*** Settings ***
Resource            ../../../flow/governance/gates/gates_flow.resource

*** Keywords ***
GOVERNANCE - GATE BLOCKS AND RELEASES
    [Documentation]    Cenário do gate: configuração, bloqueio, aprovação por papel e liberação (#78).
    [Arguments]    ${title}    ${gate_name}    ${to_key}
    Gates - Bloquear Aprovar E Liberar    ${title}    ${gate_name}    ${to_key}

GOVERNANCE - RULE NOT AVAILABLE
    [Documentation]    Cenário da regra que ainda não tem avaliador (#78).
    [Arguments]    ${title}    ${to_key}
    Gates - Regra Ainda Indisponivel    ${title}    ${to_key}

TEARDOWN: REMOVE TEST GATE
    [Documentation]    Remove o gate criado pelo teste, mesmo quando ele falha.
    Gates - Remover Gate Do Teste
