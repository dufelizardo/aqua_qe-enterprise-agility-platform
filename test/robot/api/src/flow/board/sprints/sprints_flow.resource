*** Settings ***
Documentation       Sprints opcionais por squad (SSD 3.9; PRD-RF-015): escolhe o caminho pelo status esperado e faz as
...                 asserções. Cada caso cria a própria squad.
Resource            ../../../pom/board/sprints/sprints_pom.resource
Resource            ../../workflow/transitions/transitions_flow.resource

*** Keywords ***
Create Squad
    [Documentation]    Portfólio, Cadeia e Squad novos; devolve o id da squad.
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${squad}=    Create Org Unit As Admin    Robot Squad SP    SQUAD    ${cadeia}
    RETURN    ${squad}

Create Sprint For Squad
    [Arguments]    ${persona}    ${squad}    ${name}
    ${payload}=    Create Dictionary    squadId=${squad}    name=${name}    goal=Entregar o combinado
    ${response}=    POST Sprint As Persona    ${persona}    ${payload}
    RETURN    ${response}

POST Sprint - Status Code 201 - Ciclo Completo
    [Documentation]    Cria, inclui dois itens, inicia e encerra: o item não concluído volta ao backlog.
    [Arguments]    ${name}
    ${squad}=    Create Squad
    ${response}=    Create Sprint For Squad    pm    ${squad}    ${name}
    Should Be Equal As Integers    ${response.status_code}    201
    Should Be Equal    ${response.json()}[state]    PLANNED
    ${sprint}=    Set Variable    ${response.json()}[id]
    ${done}=    Create Artifact    pm    TASK    Robot Task Feita    ${squad}
    ${pending}=    Create Artifact    pm    TASK    Robot Task Pendente    ${squad}
    ${add}=    PUT Sprint Item As Persona    pm    ${sprint}    ${done}[key]
    Should Be Equal As Integers    ${add.status_code}    204
    ${add}=    PUT Sprint Item As Persona    pm    ${sprint}    ${pending}[key]
    Should Be Equal As Integers    ${add.status_code}    204
    ${start}=    POST Sprint Action As Persona    pm    ${sprint}    start
    Should Be Equal As Integers    ${start.status_code}    200
    Should Be Equal    ${start.json()}[state]    ACTIVE
    ${to}=    Transition Id To    pm    ${done}[key]    IN_PROGRESS
    ${move}=    Create Dictionary    transitionId=${to}    version=${0}
    ${moved}=    POST Transition As Persona    pm    ${done}[key]    ${move}
    Should Be Equal As Integers    ${moved.status_code}    200
    ${to}=    Transition Id To    pm    ${done}[key]    DONE
    ${move}=    Create Dictionary    transitionId=${to}    version=${1}
    ${moved}=    POST Transition As Persona    pm    ${done}[key]    ${move}
    Should Be Equal As Integers    ${moved.status_code}    200
    ${close}=    POST Sprint Action As Persona    pm    ${sprint}    close
    Should Be Equal As Integers    ${close.status_code}    200
    Should Be Equal    ${close.json()}[sprint][state]    CLOSED
    Should Be Equal As Integers    ${close.json()}[returnedToBacklog]    1
    ${items}=    GET Sprint Items As Persona    pm    ${sprint}
    ${keys}=    Evaluate    [i['key'] for i in $items.json()['items']]
    Should Be Equal    ${keys}    ${{[$done['key']]}}

POST Sprint - Status Code 403
    [Documentation]    Membro de squad não gere sprints (matriz do SSD 2: gerir quadros e sprints).
    [Arguments]    ${name}
    ${squad}=    Create Squad
    ${response}=    Create Sprint For Squad    squad    ${squad}    ${name}
    Validate Error Body    ${response}    403    FORBIDDEN

POST Sprint Start - Status Code 409 - Ja Existe Ativa
    [Documentation]    Uma squad tem no máximo uma sprint ativa.
    [Arguments]    ${name}
    ${squad}=    Create Squad
    ${first}=    Create Sprint For Squad    pm    ${squad}    ${name} 1
    ${second}=    Create Sprint For Squad    pm    ${squad}    ${name} 2
    ${start}=    POST Sprint Action As Persona    pm    ${first.json()}[id]    start
    Should Be Equal As Integers    ${start.status_code}    200
    ${start}=    POST Sprint Action As Persona    pm    ${second.json()}[id]    start
    Validate Error Body    ${start}    409    SPRINT_ALREADY_ACTIVE

Board - Post Sprint - POST - By HTTP Status Code
    [Documentation]    Executa o cenário de sprint correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${name}
    IF    '${meuHTTP}' == '201'
        POST Sprint - Status Code 201 - Ciclo Completo    ${name}
    ELSE IF    '${meuHTTP}' == '403'
        POST Sprint - Status Code 403    ${name}
    ELSE IF    '${meuHTTP}' == '409'
        POST Sprint Start - Status Code 409 - Ja Existe Ativa    ${name}
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para sprints.
    END
