*** Settings ***
Documentation       Pessoas e privacidade (#32; REV-14; SSD 2.2). A anonimização de verdade não roda aqui: as personas
...                 são contas reais de todas as suítes, e a conta anonimizada não volta. O fluxo completo está no JUnit
...                 PrivacyIntegrationTests; aqui ficam o diretório, a exportação e as recusas.
Resource            ../../../pom/privacy/people/people_pom.resource
Resource            ../../../pom/identity/get_me/get_me_pom.resource

*** Keywords ***
Pessoas - Diretorio E Exportacao
    [Documentation]    O administrador acha a persona pelo usuário, com o e-mail, e baixa os dados dela em JSON; quem
    ...    não é administrador recebe 403.
    [Arguments]    ${persona}    ${username_part}
    ${me}=    GET Me As Persona    ${persona}
    ${person_id}=    Set Variable    ${me.json()}[id]
    ${found}=    GET People Directory As Persona    admin    ${username_part}
    Should Be Equal As Integers    ${found.status_code}    200
    ${ids}=    Evaluate    [p['id'] for p in $found.json()['items']]
    Should Contain    ${ids}    ${person_id}
    ${denied}=    GET People Directory As Persona    viewer    ${username_part}
    Should Be Equal As Integers    ${denied.status_code}    403
    ${export}=    GET Personal Data As Persona    admin    ${person_id}
    Should Be Equal As Integers    ${export.status_code}    200
    Should Contain    ${export.headers}[Content-Disposition]    dados-pessoais-
    Should Be Equal    ${export.json()}[personId]    ${person_id}
    Dictionary Should Contain Key    ${export.json()}[sections]    identity
    Dictionary Should Contain Key    ${export.json()}[sections]    access
    ${export_denied}=    GET Personal Data As Persona    viewer    ${person_id}
    Should Be Equal As Integers    ${export_denied.status_code}    403

Pessoas - Anonimizacao Recusada
    [Documentation]    Quem não é administrador não anonimiza (403); o administrador não anonimiza a si mesmo (422);
    ...    pessoa inexistente é 404.
    [Arguments]    ${persona}    ${missing_id}
    ${me}=    GET Me As Persona    ${persona}
    ${denied}=    POST Anonymize As Persona    viewer    ${me.json()}[id]
    Should Be Equal As Integers    ${denied.status_code}    403
    ${admin}=    GET Me As Persona    admin
    ${self}=    POST Anonymize As Persona    admin    ${admin.json()}[id]
    Should Be Equal As Integers    ${self.status_code}    422
    Should Be Equal    ${self.json()}[code]    CANNOT_ANONYMIZE_SELF
    ${missing}=    POST Anonymize As Persona    admin    ${missing_id}
    Should Be Equal As Integers    ${missing.status_code}    404
