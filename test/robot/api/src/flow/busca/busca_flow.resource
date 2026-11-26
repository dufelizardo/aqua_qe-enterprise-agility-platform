*** Settings ***
Documentation       Busca (SSD 3.10; #84): termos sem diferença de maiúsculas e acentos, trecho do texto e validação.
...                 As personas têm papel na raiz e leem tudo; quem não lê o item não o encontra (JUnit
...                 SearchIntegrationTests).
Library             Collections
Resource            ../../pom/busca/busca_pom.resource
Resource            ../knowledge/pages/pages_flow.resource
Resource            ../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Busca - Artefato E Pagina
    [Documentation]    O artefato é achado pelo título sem acento; a página, pelo texto salvo, com o trecho.
    [Arguments]    ${unit_name}    ${artifact_title}    ${page_title}    ${page_text}
    ${token}=    Evaluate    uuid.uuid4().hex[:10]    modules=uuid
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${artifact}=    Create Artifact    pm    INITIATIVE    ${artifact_title} ${token}    ${unit}
    ${page}=    Create Page    pm    ${unit}    ${page_title} ${token}
    ${saved}=    Save Page    pm    ${page}    1    ${page_text} ${token}
    Should Be Equal As Integers    ${saved.status_code}    200
    ${found}=    GET Search As Persona    viewer    governanca ${token}
    Should Be Equal As Integers    ${found.status_code}    200
    Should Be Equal    ${found.json()}[artifacts][0][key]    ${artifact}[key]
    ${found}=    GET Search As Persona    viewer    SEGURANCA ${token}
    Should Be Equal    ${found.json()}[pages][0][pageId]    ${page}[id]
    Should Contain    ${found.json()}[pages][0][snippet]    segurança
    ${only}=    GET Search As Persona    viewer    governanca ${token}    PAGE
    Should Be Empty    ${only.json()}[artifacts]

Busca - Validacao
    [Documentation]    Busca de menos de 2 caracteres e tipo desconhecido são 400.
    ${short}=    GET Search As Persona    viewer    a
    Validate Error Body    ${short}    400    VALIDATION_FAILED
    ${kind}=    GET Search As Persona    viewer    abc    OUTRO
    Validate Error Body    ${kind}    400    VALIDATION_FAILED
