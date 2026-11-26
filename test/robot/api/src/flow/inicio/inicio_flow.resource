*** Settings ***
Documentation       Home (#83): "Continuar de onde parou" e "Novidades". As personas têm papel na raiz, então leem as
...                 unidades criadas aqui; quem não lê o item (404) é coberto no JUnit ActivityIntegrationTests.
Library             Collections
Resource            ../../pom/inicio/inicio_pom.resource
Resource            ../knowledge/pages/pages_flow.resource
Resource            ../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Home - Recentes
    [Documentation]    Visitas registradas (204) aparecem da mais nova para a mais antiga, com o título atual; tipo
    ...    inválido é 400.
    [Arguments]    ${unit_name}    ${page_title}    ${artifact_title}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${page}=    Create Page    pm    ${unit}    ${page_title}
    ${artifact}=    Create Artifact    pm    INITIATIVE    ${artifact_title}    ${unit}
    ${response}=    POST Recent Visit As Persona    viewer    ARTIFACT    ${artifact}[key]
    Should Be Equal As Integers    ${response.status_code}    204
    ${response}=    POST Recent Visit As Persona    viewer    DOCUMENT    ${artifact}[key]/PRD
    Should Be Equal As Integers    ${response.status_code}    204
    ${response}=    POST Recent Visit As Persona    viewer    PAGE    ${page}[id]
    Should Be Equal As Integers    ${response.status_code}    204
    ${recent}=    GET Recent As Persona    viewer
    Should Be Equal As Integers    ${recent.status_code}    200
    ${items}=    Set Variable    ${recent.json()}[items]
    Should Be Equal    ${items}[0][type]    PAGE
    Should Be Equal    ${items}[0][title]    ${page_title}
    Should Be Equal    ${items}[1][type]    DOCUMENT
    Should Be Equal    ${items}[1][documentType]    PRD
    Should Be Equal    ${items}[2][artifactKey]    ${artifact}[key]
    Should Be Equal    ${items}[2][title]    ${artifact_title}
    ${invalid}=    POST Recent Visit As Persona    viewer    DOCUMENT    ${artifact}[key]/XYZ
    Validate Error Body    ${invalid}    400    VALIDATION_FAILED

Home - Novidades
    [Documentation]    A mudança de outra pessoa aparece nas novidades de quem lê a unidade; para quem fez, "own".
    [Arguments]    ${unit_name}    ${artifact_title}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${artifact}=    Create Artifact    pm    INITIATIVE    ${artifact_title}    ${unit}
    Wait Until Keyword Succeeds    30s    1s    News Should Include    viewer    ${artifact}[key]    ${False}
    News Should Include    pm    ${artifact}[key]    ${True}

News Should Include
    [Documentation]    A novidade do artefato está na lista da persona (a outbox entrega em segundos).
    [Arguments]    ${persona}    ${key}    ${own}
    ${news}=    GET News As Persona    ${persona}
    Should Be Equal As Integers    ${news.status_code}    200
    ${matches}=    Evaluate    [n for n in $news.json()['items'] if n['artifactKey'] == $key]
    Should Not Be Empty    ${matches}
    Should Be Equal    ${matches}[0][action]    artifact.created
    Should Be Equal    ${matches}[0][own]    ${own}
