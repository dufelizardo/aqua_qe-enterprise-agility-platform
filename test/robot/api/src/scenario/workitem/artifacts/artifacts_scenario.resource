*** Settings ***
Resource            ../../../flow/workitem/artifacts/artifacts_flow.resource

*** Keywords ***
WORKITEM - GET ARTIFACT TYPES - GET
    [Documentation]    Cenário de GET /api/v1/artifact-types.
    [Arguments]    ${persona}
    GET Artifact Types - Status Code 200    ${persona}

WORKITEM - POST ARTIFACT - POST
    [Documentation]    Cenário de POST /api/v1/artifacts pelo status esperado.
    [Arguments]    ${meuHTTP}    ${initiative_title}    ${initiative_summary}    ${feature_title}
    Workitem - Post Artifact - POST - By HTTP Status Code    ${meuHTTP}    ${initiative_title}    ${initiative_summary}
    ...    ${feature_title}

WORKITEM - GET ARTIFACTS - GET - Sem Workspace
    [Documentation]    Cenário de GET /api/v1/artifacts sem X-Workspace-Id.
    GET Artifacts - Status Code 400 - Sem Workspace

WORKITEM - GET ARTIFACT - GET - Inexistente
    [Documentation]    Cenário de GET /api/v1/artifacts/{artifact} inexistente.
    [Arguments]    ${key}
    GET Artifact - Status Code 404    ${key}

WORKITEM - DELETE ARTIFACT - DELETE - Filhos Ativos
    [Documentation]    Cenário de DELETE /api/v1/artifacts/{artifact} com filhos ativos.
    [Arguments]    ${initiative_title}    ${feature_title}
    DELETE Artifact - Status Code 409 - Filhos Ativos    ${initiative_title}    ${feature_title}

WORKITEM - PATCH ARTIFACT - PATCH - Titulo E Responsavel
    [Documentation]    Cenário de PATCH /api/v1/artifacts/{artifact} e GET .../people na página do artefato.
    [Arguments]    ${initiative_title}    ${new_title}
    PATCH Artifact - Status Code 200 - Titulo E Responsavel    ${initiative_title}    ${new_title}

WORKITEM - PATCH ARTIFACT - PATCH - Versao Velha E Leitor
    [Documentation]    Cenário de PATCH /api/v1/artifacts/{artifact} com versão velha e de leitura sem edição.
    [Arguments]    ${initiative_title}
    PATCH Artifact - Status Code 409 - Versao Velha E Leitor    ${initiative_title}

WORKITEM - GET UNIT PEOPLE - GET - Pessoas Para Item Novo
    [Documentation]    Cenário de GET /api/v1/org-units/{id}/people no formulário de item novo (#12; REV-07).
    [Arguments]    ${initiative_title}    ${unknown_type}
    GET Unit People - Status Code 200 - Pessoas Para Item Novo    ${initiative_title}    ${unknown_type}
