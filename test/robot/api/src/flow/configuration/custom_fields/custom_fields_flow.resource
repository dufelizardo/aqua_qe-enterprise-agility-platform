*** Settings ***
Documentation       Campos customizados (#17; REV-12). A chave leva um sufixo aleatório, e o campo é removido no fim, para
...                 não sobrar declaração no tipo entre execuções.
Library             Collections
Library             String
Resource            ../../../pom/configuration/custom_fields/custom_fields_pom.resource
Resource            ../../../pom/governance/gates/gates_pom.resource
Resource            ../../../pom/workflow/transitions/transitions_pom.resource
Resource            ../../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Unique Custom Field Key
    [Arguments]    ${base}
    ${suffix}=    Generate Random String    6    [LOWER][NUMBERS]
    RETURN    ${base}${suffix}

Custom Fields - Declarar Gravar E Remover
    [Documentation]    Só o administrador declara; o item aceita só o campo declarado e uma das opções; a remoção
    ...    devolve 204 e o campo sai do catálogo.
    [Arguments]    ${type}    ${key_base}    ${label}    ${options}    ${valid_option}    ${invalid_option}    ${title}
    ${key}=    Unique Custom Field Key    ${key_base}
    ${body}=    Create Dictionary    key=${key}    label=${label}    type=SELECT    options=${options}
    ${denied}=    POST Custom Field As Persona    pm    ${type}    ${body}
    Should Be Equal As Integers    ${denied.status_code}    403
    ${created}=    POST Custom Field As Persona    admin    ${type}    ${body}
    Should Be Equal As Integers    ${created.status_code}    201
    ${field}=    Set Variable    ${created.json()}[id]
    ${listed}=    GET Custom Fields As Persona    viewer    ${type}
    Should Be Equal As Integers    ${listed.status_code}    200
    ${keys}=    Evaluate    [f['key'] for f in $listed.json()['items']]
    List Should Contain Value    ${keys}    ${key}
    ${immutable}=    Create Dictionary    type=TEXT
    ${refused}=    PATCH Custom Field As Persona    admin    ${field}    ${immutable}
    Validate Error Body    ${refused}    400    VALIDATION_FAILED
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${artifact}=    Create Artifact    admin    ${type}    ${title}    ${portfolio}
    ${wrong}=    Evaluate    {'customFields': {$key: $invalid_option}, 'version': $artifact['version']}
    ${response}=    PATCH Artifact As Persona    admin    ${artifact}[key]    ${wrong}
    Validate Error Body    ${response}    400    VALIDATION_FAILED
    Should Be Equal    ${response.json()}[errors][0][field]    customFields.${key}
    ${undeclared}=    Evaluate    {'customFields': {$key + 'x': 'a'}, 'version': $artifact['version']}
    ${response}=    PATCH Artifact As Persona    admin    ${artifact}[key]    ${undeclared}
    Validate Error Body    ${response}    400    VALIDATION_FAILED
    ${right}=    Evaluate    {'customFields': {$key: $valid_option}, 'version': $artifact['version']}
    ${response}=    PATCH Artifact As Persona    admin    ${artifact}[key]    ${right}
    Should Be Equal As Integers    ${response.status_code}    200
    Should Be Equal    ${response.json()}[customFields][${key}]    ${valid_option}
    ${removed}=    DELETE Custom Field As Persona    admin    ${field}
    Should Be Equal As Integers    ${removed.status_code}    204
    ${listed}=    GET Custom Fields As Persona    viewer    ${type}
    ${keys}=    Evaluate    [f['key'] for f in $listed.json()['items']]
    List Should Not Contain Value    ${keys}    ${key}

Custom Fields - Gate Com Campo Nao Declarado
    [Documentation]    O gate que cita customFields.<chave> sem declaração é recusado em rules[0].params.field. Nada é
    ...    gravado, então o workflow da raiz fica como estava.
    [Arguments]    ${type}    ${key_base}    ${title}
    ${key}=    Unique Custom Field Key    ${key_base}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${artifact}=    Create Artifact    admin    ${type}    ${title}    ${portfolio}
    ${transitions}=    GET Transitions As Persona    admin    ${artifact}[key]
    Should Be Equal As Integers    ${transitions.status_code}    200
    ${transition}=    Set Variable    ${transitions.json()}[items][0][id]
    ${rule}=    Evaluate    {'type': 'REQUIRED_FIELD', 'params': {'field': 'customFields.' + $key}, 'message': 'Preencha.'}
    ${rules}=    Create List    ${rule}
    ${gate}=    Create Dictionary    name=Robot campo customizado    rules=${rules}
    ${response}=    PUT Gate As Persona    admin    ${transition}    ${gate}
    Validate Error Body    ${response}    400    VALIDATION_FAILED
    Should Be Equal    ${response.json()}[errors][0][field]    rules[0].params.field
