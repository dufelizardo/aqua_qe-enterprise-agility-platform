*** Settings ***
Documentation       Suíte do Workflow Designer: workflow próprio por unidade, versões e migração explícita (#49; SSD 3.3).
Resource            ../../../src/scenario/configuration/workflows/workflows_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Workflow Designer
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-27
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - SSD-3.3: Validate POST Unit Workflow, POST Version e POST Migration - HTTP 201/200 (sem migração silenciosa)
    [Documentation]    A Cadeia personaliza o workflow; a versão 2 não move o item de antes; a migração explícita o leva;
    ...    voltar a herdar devolve o workflow da raiz.
    [Tags]    POST    DELETE    Workflows    HTTP403    HTTP201    HTTP409    HTTP200    HTTP204    SSD-3.3    F05
    CONFIGURATION - WORKFLOW CUSTOMIZE PUBLISH MIGRATE    ${workflow_designer_type}    ${workflow_designer_title}
    ...    ${workflow_designer_status_key}    ${workflow_designer_status_name}

CT-002 - SSD-3.3: Validate POST Workflow Version - HTTP 400 (versão inválida e raiz)
    [Documentation]    Chave repetida e transição para status inexistente são recusadas; a raiz não volta a herdar.
    [Tags]    POST    DELETE    Workflows    HTTP400    SSD-3.3
    CONFIGURATION - WORKFLOW INVALID VERSION    ${workflow_designer_type}
