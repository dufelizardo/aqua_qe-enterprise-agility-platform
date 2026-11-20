*** Settings ***
Documentation       Transições de status (SSD 3.5 e 4.2): escolhe o caminho pelo status esperado e faz as asserções.
...                 Cria a Iniciativa com o fluxo de artefatos.
Resource            ../../../pom/workflow/transitions/transitions_pom.resource
Resource            ../../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Create Initiative For Transitions
    [Arguments]    ${title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${title}    ${portfolio}
    RETURN    ${init}

Transition Id To
    [Documentation]    Id da transição que leva ao status informado, a partir do status atual.
    [Arguments]    ${persona}    ${artifact}    ${to_key}
    ${response}=    GET Transitions As Persona    ${persona}    ${artifact}
    Should Be Equal As Integers    ${response.status_code}    200
    ${ids}=    Evaluate    [t['id'] for t in $response.json()['items'] if t['to']['key'] == $to_key]
    Should Not Be Empty    ${ids}    msg=Não há transição para ${to_key}.
    RETURN    ${ids}[0]

GET Transitions - Status Code 200
    [Documentation]    Da Ideia, a Iniciativa pode ir para Backlog ou ser cancelada; o PM pode executar.
    [Arguments]    ${title}
    ${init}=    Create Initiative For Transitions    ${title}
    ${response}=    GET Transitions As Persona    pm    ${init}[key]
    Should Be Equal As Integers    ${response.status_code}    200
    ${targets}=    Evaluate    sorted(t['to']['key'] for t in $response.json()['items'])
    ${expected}=    Create List    BACKLOG    CANCELLED
    Lists Should Be Equal    ${targets}    ${expected}
    ${can}=    Evaluate    all(t['canExecute'] for t in $response.json()['items'])
    Should Be True    ${can}

POST Transition - Status Code 200
    [Documentation]    Executa Ideia → Backlog; o artefato volta atualizado e o histórico registra a mudança.
    [Arguments]    ${title}    ${comment}
    ${init}=    Create Initiative For Transitions    ${title}
    ${transition}=    Transition Id To    pm    ${init}[key]    BACKLOG
    ${payload}=    Create Dictionary    transitionId=${transition}    version=${0}    comment=${comment}
    ${response}=    POST Transition As Persona    pm    ${init}[key]    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
    Should Be Equal    ${response.json()}[status][key]    BACKLOG
    Should Be Equal As Integers    ${response.json()}[version]    1
    ${history}=    GET History As Persona    pm    ${init}[key]
    Should Be Equal As Integers    ${history.status_code}    200
    ${items}=    Set Variable    ${history.json()}[items]
    Length Should Be    ${items}    1
    Should Be Equal    ${items}[0][from]    IDEA
    Should Be Equal    ${items}[0][to]    BACKLOG
    Should Be Equal    ${items}[0][comment]    ${comment}

POST Transition - Status Code 409
    [Documentation]    Versão desatualizada é recusada e nada muda.
    [Arguments]    ${title}
    ${init}=    Create Initiative For Transitions    ${title}
    ${transition}=    Transition Id To    pm    ${init}[key]    BACKLOG
    ${payload}=    Create Dictionary    transitionId=${transition}    version=${7}
    ${response}=    POST Transition As Persona    pm    ${init}[key]    ${payload}
    Validate Error Body    ${response}    409    VERSION_CONFLICT

POST Transition - Status Code 422
    [Documentation]    Uma transição que não sai do status atual é recusada (TRANSITION_NOT_ALLOWED).
    [Arguments]    ${title}
    ${init}=    Create Initiative For Transitions    ${title}
    ${to_backlog}=    Transition Id To    pm    ${init}[key]    BACKLOG
    ${payload}=    Create Dictionary    transitionId=${to_backlog}    version=${0}
    ${response}=    POST Transition As Persona    pm    ${init}[key]    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
    # A mesma transição (Ideia → Backlog) não sai mais do status atual, que agora é Backlog.
    ${payload}=    Create Dictionary    transitionId=${to_backlog}    version=${1}
    ${response}=    POST Transition As Persona    pm    ${init}[key]    ${payload}
    Validate Error Body    ${response}    422    TRANSITION_NOT_ALLOWED

Workflow - Post Transition - POST - By HTTP Status Code
    [Documentation]    Executa o cenário de POST /artifacts/{artifact}/transitions correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${title}    ${comment}
    IF    '${meuHTTP}' == '200'
        POST Transition - Status Code 200    ${title}    ${comment}
    ELSE IF    '${meuHTTP}' == '409'
        POST Transition - Status Code 409    ${title}
    ELSE IF    '${meuHTTP}' == '422'
        POST Transition - Status Code 422    ${title}
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para POST /artifacts/{artifact}/transitions.
    END
