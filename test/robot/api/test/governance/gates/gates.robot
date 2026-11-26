*** Settings ***
Documentation       Suíte dos gates por transição (SSD 5; #78; PRD-RF-022 a 024).
Resource            ../../../src/scenario/governance/gates/gates_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Test Teardown       TEARDOWN: REMOVE TEST GATE
Metadata            Suite    Gates
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-26
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-022: Validate PUT Gate e POST Transition - HTTP 422 GATE_BLOCKED até atender, depois 200
    [Documentation]    Só o administrador configura; as regras não atendidas bloqueiam; a aprovação é do papel; depois passa.
    [Tags]    PUT    POST    Gates    Approvals    HTTP403    HTTP422    HTTP200    PRD-RF-022    PRD-RF-023    PRD-RF-024
    GOVERNANCE - GATE BLOCKS AND RELEASES    ${gate_initiative_title}    ${gate_name}    ${gate_to_key}

CT-002 - PRD-RF-022: Validate PUT Gate - HTTP 400 (regra que depende do business case)
    [Documentation]    BUSINESS_CASE_VALIDATED ainda não tem avaliador: o gate é recusado com a explicação.
    [Tags]    PUT    Gates    HTTP400    PRD-RF-022
    GOVERNANCE - RULE NOT AVAILABLE    ${gate_initiative_title}    ${gate_to_key}
