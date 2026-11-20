*** Settings ***
Documentation       Suíte de artefatos (SSD 3.4) e da configuração padrão (SSD 3.3 e 8.1). O isolamento entre ramos
...                 (SSD 2.1) é coberto pelos testes JUnit: os usuários robot.<persona> têm papel na raiz.
Resource            ../../../src/scenario/workitem/artifacts/artifacts_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Artifacts
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-012: Validate GET Artifact Types - HTTP 200 OK
    [Documentation]    Os onze tipos da instalação padrão, com nível e pais permitidos.
    [Tags]    GET    GetArtifactTypes    HTTP200    PRD-RF-012    PRD-RF-016    smoke
    WORKITEM - GET ARTIFACT TYPES - GET    viewer

CT-002 - PRD-RF-010: Validate POST Artifact - HTTP 201 CREATED (Iniciativa, Feature e contexto herdado)
    [Documentation]    Iniciativa no Portfólio, Feature na Cadeia sob ela, contexto e listagem.
    [Tags]    POST    PostArtifact    HTTP201    PRD-RF-001    PRD-RF-010    PRD-RF-017    smoke
    WORKITEM - POST ARTIFACT - POST    201    ${post_artifact_initiative_title}    ${post_artifact_initiative_summary}
    ...    ${post_artifact_feature_title}

CT-003 - REV-01: Validate POST Artifact - HTTP 403 FORBIDDEN (papel sem permissão no nível)
    [Documentation]    Tech Lead não cria Iniciativa.
    [Tags]    POST    PostArtifact    HTTP403    REV-01    security    ASVS-V4
    WORKITEM - POST ARTIFACT - POST    403    ${post_artifact_initiative_title}    ${post_artifact_initiative_summary}
    ...    ${post_artifact_feature_title}

CT-004 - PRD-RF-010: Validate POST Artifact - HTTP 422 INVALID_PARENT_TYPE
    [Documentation]    Uma História não fica sob uma Iniciativa.
    [Tags]    POST    PostArtifact    HTTP422    PRD-RF-010
    WORKITEM - POST ARTIFACT - POST    422    ${post_artifact_initiative_title}    ${post_artifact_initiative_summary}
    ...    ${post_artifact_feature_title}

CT-005 - PRD-RF-038: Validate GET Artifacts - HTTP 400 WORKSPACE_REQUIRED
    [Documentation]    Listagem sem workspace é recusada.
    [Tags]    GET    GetArtifacts    HTTP400    PRD-RF-038
    WORKITEM - GET ARTIFACTS - GET - Sem Workspace

CT-006 - PRD-RF-010: Validate GET Artifact - HTTP 404 NOT FOUND
    [Documentation]    Chave inexistente responde 404.
    [Tags]    GET    GetArtifact    HTTP404    PRD-RF-010
    WORKITEM - GET ARTIFACT - GET - Inexistente    ${post_artifact_unknown_key}

CT-007 - PRD-RF-010: Validate DELETE Artifact - HTTP 409 HAS_CHILDREN
    [Documentation]    Arquivar exige que não haja filhos ativos.
    [Tags]    DELETE    DeleteArtifact    HTTP409    PRD-RF-010
    WORKITEM - DELETE ARTIFACT - DELETE - Filhos Ativos    ${post_artifact_initiative_title}
    ...    ${post_artifact_feature_title}
