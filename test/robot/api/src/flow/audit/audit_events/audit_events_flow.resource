*** Settings ***
Documentation       Trilha de auditoria (SSD 3.10; SAD 7.5; REV-03): escolhe o caminho pelo status esperado e faz as
...                 asserções. A entrega é assíncrona; o fluxo espera o registro aparecer.
Library             Collections
Resource            ../../../pom/audit/audit_events/audit_events_pom.resource
Resource            ../../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Audit Should Contain
    [Documentation]    O registro da ação para o recurso já está na trilha.
    [Arguments]    ${target_id}    ${action}
    ${params}=    Create Dictionary    targetId=${target_id}    action=${action}
    ${response}=    GET Audit Events As Persona    admin    ${params}
    Should Be Equal As Integers    ${response.status_code}    200
    Should Be Equal As Integers    ${response.json()}[totalItems]    1
    RETURN    ${response.json()}[items][0]

GET Audit Events - Status Code 200
    [Documentation]    Criar uma Iniciativa gera artifact.created na trilha, com ator e valores; a cadeia fica íntegra.
    [Arguments]    ${title}    ${wait}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${title}    ${portfolio}
    ${record}=    Wait Until Keyword Succeeds    ${wait}    1s    Audit Should Contain    ${init}[id]    artifact.created
    Should Be Equal    ${record}[target][key]    ${init}[key]
    Should Be Equal    ${record}[after][title]    ${title}
    Should Not Be Empty    ${record}[hash]
    ${verification}=    GET Audit Verification As Persona    admin
    Should Be Equal As Integers    ${verification.status_code}    200
    Should Be True    ${verification.json()}[valid]

GET Audit Events - Status Code 403
    [Documentation]    Só o administrador consulta a auditoria (matriz do SSD 2).
    ${response}=    GET Audit Events As Persona    pm
    Validate Error Body    ${response}    403    FORBIDDEN

Audit - Get Audit Events - GET - By HTTP Status Code
    [Documentation]    Executa o cenário de GET /audit-events correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${title}    ${wait}
    IF    '${meuHTTP}' == '200'
        GET Audit Events - Status Code 200    ${title}    ${wait}
    ELSE IF    '${meuHTTP}' == '403'
        GET Audit Events - Status Code 403
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para GET /audit-events.
    END

Audit - Garantias Da Entrega
    [Documentation]    #8 (REV-03): a situação traz o SLA (alvo 60 s, alerta 300 s) e o atraso; a verificação conta as
    ...    âncoras; âncoras e fila de falhas são só do administrador. A adulteração e a fila cheia ficam no JUnit
    ...    AuditGuaranteesIntegrationTests, porque exigem mexer no banco.
    ${status}=    GET Audit Status As Persona    admin
    Should Be Equal As Integers    ${status.status_code}    200
    Should Be Equal As Integers    ${status.json()}[targetSeconds]    60
    Should Be Equal As Integers    ${status.json()}[alertSeconds]    300
    Dictionary Should Contain Key    ${status.json()}    lagSeconds
    Dictionary Should Contain Key    ${status.json()}    deadLetters
    ${verification}=    GET Audit Verification As Persona    admin
    Should Be True    ${verification.json()}[valid]
    Dictionary Should Contain Key    ${verification.json()}    anchorsChecked
    ${anchors}=    GET Audit Anchors As Persona    admin
    Should Be Equal As Integers    ${anchors.status_code}    200
    ${failures}=    GET Event Failures As Persona    admin
    Should Be Equal As Integers    ${failures.status_code}    200
    FOR    ${call}    IN    GET Audit Status As Persona    GET Audit Anchors As Persona    GET Event Failures As Persona
        ${denied}=    Run Keyword    ${call}    pm
        Should Be Equal As Integers    ${denied.status_code}    403
    END
