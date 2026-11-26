*** Settings ***
Documentation       Responsável padrão (#43): regra por unidade e tipo, herdada da unidade acima. Cada teste cria o
...                 próprio Portfólio, então a regra gravada não afeta as outras suítes.
Library             Collections
Resource            ../../../pom/workitem/default_owner/default_owner_pom.resource
Resource            ../../../pom/identity/get_me/get_me_pom.resource
Resource            ../artifacts/artifacts_flow.resource

*** Keywords ***
Default Owner - Quem Criou Fica Como Owner
    [Documentation]    Só o administrador grava a regra; com "quem criou", o item sem ownerId nasce com o criador; a
    ...    sugestão do formulário indica a mesma pessoa; sem a regra própria, a unidade volta a herdar.
    [Arguments]    ${title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${rule}=    Create Dictionary    mode=CREATOR
    ${denied}=    PUT Default Owner Rule As Persona    pm    ${portfolio}    INITIATIVE    ${rule}
    Should Be Equal As Integers    ${denied.status_code}    403
    ${saved}=    PUT Default Owner Rule As Persona    admin    ${portfolio}    INITIATIVE    ${rule}
    Should Be Equal As Integers    ${saved.status_code}    200
    Should Be Equal    ${saved.json()}[mode]    CREATOR
    Should Not Be True    ${saved.json()}[inherited]
    ${me}=    GET Me As Persona    pm
    ${init}=    Create Artifact    pm    INITIATIVE    ${title}    ${portfolio}
    Should Be Equal    ${init}[owner][id]    ${me.json()}[id]
    ${suggestion}=    GET Default Owner Suggestion As Persona    pm    ${portfolio}    INITIATIVE
    Should Be Equal As Integers    ${suggestion.status_code}    200
    Should Be Equal    ${suggestion.json()}[suggestedId]    ${me.json()}[id]
    ${removed}=    DELETE Default Owner Rule As Persona    admin    ${portfolio}    INITIATIVE
    Should Be Equal As Integers    ${removed.status_code}    204
    ${rules}=    GET Default Owner Rules As Persona    pm    ${portfolio}
    ${initiative}=    Evaluate    [r for r in $rules.json()['items'] if r['artifactType']['key'] == 'INITIATIVE'][0]
    Should Be True    ${initiative}[inherited]

Default Owner - Regra Invalida
    [Documentation]    Regra por papel sem papel, ou com o administrador da plataforma, é recusada.
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${no_role}=    Create Dictionary    mode=ROLE
    ${refused}=    PUT Default Owner Rule As Persona    admin    ${portfolio}    INITIATIVE    ${no_role}
    Validate Error Body    ${refused}    400    VALIDATION_FAILED
    ${admin_role}=    Create Dictionary    mode=ROLE    role=PLATFORM_ADMIN
    ${refused}=    PUT Default Owner Rule As Persona    admin    ${portfolio}    INITIATIVE    ${admin_role}
    Validate Error Body    ${refused}    400    VALIDATION_FAILED
