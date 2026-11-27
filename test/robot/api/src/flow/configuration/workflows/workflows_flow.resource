*** Settings ***
Documentation       Workflow Designer (#49; SSD 3.3; F05/D-01). Cada teste cria a própria Cadeia, então o workflow
...                 personalizado nela não afeta as outras suítes.
Library             Collections
Resource            ../../../pom/configuration/workflows/workflows_pom.resource
Resource            ../../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Root Workflow Id For Type
    [Documentation]    O workflow da raiz que cobre o tipo (os padrões da seção 8.2 do SSD).
    [Arguments]    ${type}
    ${params}=    Create Dictionary    artifactType=${type}
    ${response}=    GET Workflows As Persona    viewer    ${params}
    Should Be Equal As Integers    ${response.status_code}    200
    ${root}=    Get Root Org Unit Id
    ${ids}=    Evaluate    [w['id'] for w in $response.json()['items'] if w['orgUnit']['id'] == $root]
    RETURN    ${ids}[0]

Version Body With New Status
    [Documentation]    Corpo de uma nova versão: os status e as transições da versão atual, mais um status novo logo
    ...    depois do primeiro, com transição do primeiro para ele.
    [Arguments]    ${workflow}    ${key}    ${name}
    ${current}=    GET Workflow As Persona    admin    ${workflow}
    Should Be Equal As Integers    ${current.status_code}    200
    ${json}=    Set Variable    ${current.json()}
    ${statuses}=    Evaluate
    ...    [dict(key=s['key'], name=s['name'], phase=s['phase'], category=s['category'], inSequence=s['position'] is not None) for s in $json['statuses']]
    ${transitions}=    Evaluate    [{'from': t['from'], 'to': t['to']} for t in $json['transitions']]
    ${first}=    Set Variable    ${statuses}[0][key]
    ${status}=    Create Dictionary    key=${key}    name=${name}    phase=UPSTREAM    category=IN_PROGRESS
    ...    inSequence=${True}
    Insert Into List    ${statuses}    1    ${status}
    ${transition}=    Evaluate    {'from': $first, 'to': $key}
    Append To List    ${transitions}    ${transition}
    ${body}=    Create Dictionary    statuses=${statuses}    transitions=${transitions}
    RETURN    ${body}

Workflows - Personalizar Publicar E Migrar
    [Documentation]    Só o administrador personaliza; a cópia vale para a Cadeia; o item de antes fica no workflow
    ...    herdado até a migração; publicar não move ninguém; migrar leva o item; voltar a herdar volta ao de cima.
    [Arguments]    ${type}    ${title}    ${status_key}    ${status_name}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${root_workflow}=    Root Workflow Id For Type    ${type}
    Create Artifact    admin    ${type}    ${title}    ${cadeia}
    ${from}=    Create Dictionary    from=${root_workflow}
    ${denied}=    POST Unit Workflow As Persona    pm    ${cadeia}    ${from}
    Should Be Equal As Integers    ${denied.status_code}    403
    ${copy}=    POST Unit Workflow As Persona    admin    ${cadeia}    ${from}
    Should Be Equal As Integers    ${copy.status_code}    201
    Should Be Equal    ${copy.json()}[orgUnit][id]    ${cadeia}
    Should Be Equal As Integers    ${copy.json()}[versionNumber]    1
    ${v1}=    Set Variable    ${copy.json()}[id]
    ${unit}=    GET Unit Workflows As Persona    pm    ${cadeia}
    ${own}=    Evaluate    [w for w in $unit.json()['items'] if w['workflow']['id'] == $v1][0]
    Should Not Be True    ${own}[inherited]
    ${body}=    Version Body With New Status    ${v1}    ${status_key}    ${status_name}
    ${v2}=    POST Workflow Version As Persona    admin    ${v1}    ${body}
    Should Be Equal As Integers    ${v2.status_code}    201
    Should Be Equal As Integers    ${v2.json()}[versionNumber]    2
    ${keys}=    Evaluate    [s['key'] for s in $v2.json()['statuses']]
    List Should Contain Value    ${keys}    ${status_key}
    ${stale}=    POST Workflow Version As Persona    admin    ${v1}    ${body}
    Should Be Equal As Integers    ${stale.status_code}    409
    ${v2_id}=    Set Variable    ${v2.json()}[id]
    ${preview}=    GET Workflow Migration As Persona    admin    ${v2_id}
    Should Be Equal As Integers    ${preview.status_code}    200
    Should Be Equal As Integers    ${preview.json()}[total]    1
    Should Be Equal    ${preview.json()}[pending][0][suggestedKey]    ${preview.json()}[pending][0][statusKey]
    ${empty}=    Create Dictionary
    ${payload}=    Create Dictionary    mapping=${empty}
    ${migrated}=    POST Workflow Migration As Persona    admin    ${v2_id}    ${payload}
    Should Be Equal As Integers    ${migrated.status_code}    200
    Should Be Equal As Integers    ${migrated.json()}[migrated]    1
    ${after}=    GET Workflow Migration As Persona    admin    ${v2_id}
    Should Be Equal As Integers    ${after.json()}[total]    0
    ${reverted}=    DELETE Workflow As Persona    admin    ${v2_id}
    Should Be Equal As Integers    ${reverted.status_code}    204
    ${unit}=    GET Unit Workflows As Persona    pm    ${cadeia}
    ${back}=    Evaluate    [w for w in $unit.json()['items'] if w['workflow']['id'] == $root_workflow][0]
    Should Be True    ${back}[inherited]

Workflows - Versao Invalida
    [Documentation]    Chave repetida e transição para status inexistente são recusadas; a raiz não volta a herdar.
    [Arguments]    ${type}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${root_workflow}=    Root Workflow Id For Type    ${type}
    ${from}=    Create Dictionary    from=${root_workflow}
    ${copy}=    POST Unit Workflow As Persona    admin    ${cadeia}    ${from}
    Should Be Equal As Integers    ${copy.status_code}    201
    ${status}=    Create Dictionary    key=A    name=A    category=TODO
    ${statuses}=    Create List    ${status}    ${status}
    ${body}=    Create Dictionary    statuses=${statuses}
    ${refused}=    POST Workflow Version As Persona    admin    ${copy.json()}[id]    ${body}
    Validate Error Body    ${refused}    400    VALIDATION_FAILED
    ${statuses}=    Create List    ${status}
    ${transition}=    Evaluate    {'from': 'A', 'to': 'B'}
    ${transitions}=    Create List    ${transition}
    ${body}=    Create Dictionary    statuses=${statuses}    transitions=${transitions}
    ${refused}=    POST Workflow Version As Persona    admin    ${copy.json()}[id]    ${body}
    Validate Error Body    ${refused}    400    VALIDATION_FAILED
    ${root}=    DELETE Workflow As Persona    admin    ${root_workflow}
    Validate Error Body    ${root}    400    VALIDATION_FAILED
