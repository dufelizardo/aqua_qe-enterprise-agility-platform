*** Settings ***
Documentation       Artefatos (SSD 3.4): escolhe o caminho pelo status esperado e faz as asserções. Monta a estrutura
...                 organizacional com o fluxo do módulo organization.
Library             String
Resource            ../../../pom/workitem/artifacts/artifacts_pom.resource
Resource            ../../organization/org_units/org_units_flow.resource
Resource            ../../common/schema_validation_flow.resource
Resource            ../../../resource/config/schema/schema.resource

*** Keywords ***
Create Portfolio And Cadeia
    [Documentation]    Portfólio sob a raiz e uma Cadeia sob ele; devolve os dois ids.
    ${root}=    Get Root Org Unit Id
    ${portfolio}=    Create Org Unit As Admin    Robot Portfólio WI    PORTFOLIO    ${root}
    ${cadeia}=    Create Org Unit As Admin    Robot Cadeia WI    CADEIA    ${portfolio}
    RETURN    ${portfolio}    ${cadeia}

Create Artifact
    [Documentation]    Cria um artefato e exige 201; devolve o corpo.
    [Arguments]    ${persona}    ${type}    ${title}    ${org_unit}    ${parent}=${None}    ${description}=${None}
    ${payload}=    Create Dictionary    type=${type}    title=${title}    orgUnitId=${org_unit}
    IF    $parent is not None
        Set To Dictionary    ${payload}    parentId=${parent}
    END
    IF    $description is not None
        Set To Dictionary    ${payload}    description=${description}
    END
    ${response}=    POST Artifact As Persona    ${persona}    ${payload}
    Should Be Equal As Integers    ${response.status_code}    201
    Validate Object Schema    ${response.json()}    ${WORKITEM_ARTIFACT_SCHEMA}
    RETURN    ${response.json()}

GET Artifact Types - Status Code 200
    [Documentation]    Os onze tipos da instalação padrão (SSD 8.1), com nível, prefixo e pais permitidos.
    [Arguments]    ${persona}
    ${response}=    GET Artifact Types As Persona    ${persona}
    Should Be Equal As Integers    ${response.status_code}    200
    ${types}=    Set Variable    ${response.json()}[items]
    Validate Array Schema    ${types}    ${WORKITEM_ARTIFACT_TYPE_SCHEMA}
    Length Should Be    ${types}    11
    ${by_key}=    Evaluate    {t['key']: t for t in $types}
    Should Be Equal    ${by_key}[INITIATIVE][level]    FL3
    Should Contain    ${by_key}[FEATURE][parentTypes]    INITIATIVE
    Should Be True    ${by_key}[STORY][parentRequired]

POST Artifact - Status Code 201 - Iniciativa E Feature
    [Documentation]    O PM cria a Iniciativa no Portfólio; o TL cria a Feature na Cadeia, sob ela. O contexto herdado
    ...    da Feature traz o resumo da Iniciativa.
    [Arguments]    ${initiative_title}    ${initiative_summary}    ${feature_title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${initiative_title}    ${portfolio}
    ...    description=${initiative_summary}
    Should Start With    ${init}[key]    INIT-
    Should Be Equal    ${init}[status][key]    IDEA
    ${feat}=    Create Artifact    tl    FEATURE    ${feature_title}    ${cadeia}    parent=${init}[id]
    Should Start With    ${feat}[key]    FEAT-
    Should Be Equal    ${feat}[parent][key]    ${init}[key]
    Should Be Equal    ${feat}[status][key]    BACKLOG
    ${context}=    GET Artifact Context As Persona    tl    ${feat}[key]
    Should Be Equal As Integers    ${context.status_code}    200
    Should Be Equal    ${context.json()}[ancestors][0][key]    ${init}[key]
    Should Be Equal    ${context.json()}[ancestors][0][summary]    ${initiative_summary}
    ${listing}=    GET Artifacts As Persona    pm    ${portfolio}
    Should Be Equal As Integers    ${listing.status_code}    200
    ${keys}=    Evaluate    [a['key'] for a in $listing.json()['items']]
    Should Contain    ${keys}    ${init}[key]
    Should Contain    ${keys}    ${feat}[key]

POST Artifact - Status Code 403 - Papel Sem Permissao No Nivel
    [Documentation]    Tech Lead não cria Iniciativa (matriz do SSD 2).
    [Arguments]    ${title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${payload}=    Create Dictionary    type=INITIATIVE    title=${title}    orgUnitId=${portfolio}
    ${response}=    POST Artifact As Persona    tl    ${payload}
    Validate Error Body    ${response}    403    FORBIDDEN

POST Artifact - Status Code 422 - Tipo Do Pai
    [Documentation]    Uma História não fica sob uma Iniciativa (SSD 8.1).
    [Arguments]    ${title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${squad}=    Create Org Unit As Admin    Robot Squad WI    SQUAD    ${cadeia}
    ${init}=    Create Artifact    pm    INITIATIVE    ${title}    ${portfolio}
    ${payload}=    Create Dictionary    type=STORY    title=${title}    orgUnitId=${squad}    parentId=${init}[id]
    ${response}=    POST Artifact As Persona    pm    ${payload}
    Validate Error Body    ${response}    422    INVALID_PARENT_TYPE

GET Artifacts - Status Code 400 - Sem Workspace
    [Documentation]    Listagem sem X-Workspace-Id responde WORKSPACE_REQUIRED (SSD 1.2).
    ${response}=    GET Artifacts As Persona    pm
    Validate Error Body    ${response}    400    WORKSPACE_REQUIRED

GET Artifact - Status Code 404
    [Documentation]    Artefato inexistente responde 404.
    [Arguments]    ${key}
    ${response}=    GET Artifact As Persona    pm    ${key}
    Validate Error Body    ${response}    404    NOT_FOUND

DELETE Artifact - Status Code 409 - Filhos Ativos
    [Documentation]    Arquivar exige que não haja filhos ativos; depois de arquivar o filho, o pai arquiva.
    [Arguments]    ${initiative_title}    ${feature_title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${initiative_title}    ${portfolio}
    ${feat}=    Create Artifact    tl    FEATURE    ${feature_title}    ${cadeia}    parent=${init}[id]
    ${response}=    DELETE Artifact As Persona    pm    ${init}[key]
    Validate Error Body    ${response}    409    HAS_CHILDREN
    ${response}=    DELETE Artifact As Persona    tl    ${feat}[key]
    Should Be Equal As Integers    ${response.status_code}    204
    ${response}=    DELETE Artifact As Persona    pm    ${init}[key]
    Should Be Equal As Integers    ${response.status_code}    204

Workitem - Post Artifact - POST - By HTTP Status Code
    [Documentation]    Executa o cenário de POST /artifacts correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${initiative_title}    ${initiative_summary}    ${feature_title}
    IF    '${meuHTTP}' == '201'
        POST Artifact - Status Code 201 - Iniciativa E Feature    ${initiative_title}    ${initiative_summary}
        ...    ${feature_title}
    ELSE IF    '${meuHTTP}' == '403'
        POST Artifact - Status Code 403 - Papel Sem Permissao No Nivel    ${initiative_title}
    ELSE IF    '${meuHTTP}' == '422'
        POST Artifact - Status Code 422 - Tipo Do Pai    ${initiative_title}
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para POST /artifacts.
    END

PATCH Artifact - Status Code 200 - Titulo E Responsavel
    [Documentation]    Na página do artefato, quem edita vê canEdit, busca pessoas e troca o título e o responsável.
    [Arguments]    ${initiative_title}    ${new_title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${initiative_title}    ${portfolio}
    ${read}=    GET Artifact As Persona    pm    ${init}[key]
    Should Be True    ${read.json()}[canEdit]
    ${people}=    GET Artifact People As Persona    pm    ${init}[key]
    Should Be Equal As Integers    ${people.status_code}    200
    Should Not Be Empty    ${people.json()}[items]
    ${person}=    Set Variable    ${people.json()}[items][0]
    ${payload}=    Create Dictionary    title=${new_title}    ownerId=${person}[id]    version=${init}[version]
    ${response}=    PATCH Artifact As Persona    pm    ${init}[key]    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
    Validate Object Schema    ${response.json()}    ${WORKITEM_ARTIFACT_SCHEMA}
    Should Be Equal    ${response.json()}[title]    ${new_title}
    Should Be Equal    ${response.json()}[owner][id]    ${person}[id]

PATCH Artifact - Status Code 409 - Versao Velha E Leitor
    [Documentation]    A versão velha é recusada com VERSION_CONFLICT; o leitor não edita nem busca pessoas.
    [Arguments]    ${initiative_title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${initiative_title}    ${portfolio}
    ${first}=    Create Dictionary    title=${initiative_title} 2    version=${init}[version]
    ${response}=    PATCH Artifact As Persona    pm    ${init}[key]    ${first}
    Should Be Equal As Integers    ${response.status_code}    200
    ${stale}=    Create Dictionary    title=${initiative_title} 3    version=${init}[version]
    ${response}=    PATCH Artifact As Persona    pm    ${init}[key]    ${stale}
    Validate Error Body    ${response}    409    VERSION_CONFLICT
    ${read}=    GET Artifact As Persona    viewer    ${init}[key]
    Should Not Be True    ${read.json()}[canEdit]
    ${people}=    GET Artifact People As Persona    viewer    ${init}[key]
    Should Be Equal As Integers    ${people.status_code}    403
