*** Settings ***
Documentation       Quadros configuráveis e limite de WIP (SSD 3.9; Proposta 04): escolhe o caminho pelo status
...                 esperado e faz as asserções. Cada caso cria o próprio Portfólio e o próprio quadro.
Resource            ../../../pom/board/boards/boards_pom.resource
Resource            ../../workflow/transitions/transitions_flow.resource

*** Keywords ***
Create Portfolio Board
    [Documentation]    Portfólio novo sob a raiz e o quadro de FL3 dele; devolve os dois ids.
    ${root}=    Get Root Org Unit Id
    ${portfolio}=    Create Org Unit As Admin    Robot Portfólio Quadro    PORTFOLIO    ${root}
    ${payload}=    Create Dictionary    orgUnitId=${portfolio}    level=FL3
    ${response}=    POST Board As Persona    pm    ${payload}
    Should Be Equal As Integers    ${response.status_code}    201
    RETURN    ${portfolio}    ${response.json()}[id]

Board Columns Payload
    [Documentation]    Ideia; Backlog com limite 1 que impede a entrada; o restante numa coluna Downstream.
    ${ideia}=    Evaluate    {'name': 'Ideia', 'phase': 'UPSTREAM', 'statusKeys': ['IDEA']}
    ${backlog}=    Evaluate    {'name': 'Backlog', 'phase': 'UPSTREAM', 'statusKeys': ['BACKLOG'], 'wipLimit': 1, 'wipPolicy': 'BLOCK'}
    ${resto}=    Evaluate    {'name': 'Andamento', 'phase': 'DOWNSTREAM', 'statusKeys': ['FEASIBILITY', 'REFINEMENT', 'BUILDING', 'DONE']}
    ${columns}=    Create List    ${ideia}    ${backlog}    ${resto}
    ${payload}=    Create Dictionary    name=Robot Quadro Configurado    columns=${columns}
    RETURN    ${payload}

PATCH Board - Status Code 200
    [Documentation]    Configura nome, colunas, fases e limite de WIP; a resposta traz a configuração aplicada.
    ${portfolio}    ${board}=    Create Portfolio Board
    ${payload}=    Board Columns Payload
    ${response}=    PATCH Board As Persona    pm    ${board}    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
    Should Be Equal    ${response.json()}[name]    Robot Quadro Configurado
    Length Should Be    ${response.json()}[columns]    3
    Should Be Equal As Integers    ${response.json()}[columns][1][wipLimit]    1
    Should Be Equal    ${response.json()}[columns][1][wipPolicy]    BLOCK
    Should Be Empty    ${response.json()}[unmappedStatusKeys]

PATCH Board - Status Code 403
    [Documentation]    Membro de squad não gere quadros (matriz do SSD 2).
    ${portfolio}    ${board}=    Create Portfolio Board
    ${payload}=    Create Dictionary    name=Tentativa
    ${response}=    PATCH Board As Persona    squad    ${board}    ${payload}
    Validate Error Body    ${response}    403    FORBIDDEN

POST Transition - Status Code 422 - Limite De WIP
    [Documentation]    Com a coluna Backlog no limite (1, impedir), a segunda Iniciativa não entra: GATE_BLOCKED com
    ...    a regra WIP_LIMIT. A visão mostra a coluna no limite.
    ${portfolio}    ${board}=    Create Portfolio Board
    ${payload}=    Board Columns Payload
    ${response}=    PATCH Board As Persona    pm    ${board}    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
    ${first}=    Create Artifact    pm    INITIATIVE    Robot WIP 1    ${portfolio}
    ${second}=    Create Artifact    pm    INITIATIVE    Robot WIP 2    ${portfolio}
    ${to_backlog}=    Transition Id To    pm    ${first}[key]    BACKLOG
    ${move}=    Create Dictionary    transitionId=${to_backlog}    version=${0}
    ${response}=    POST Transition As Persona    pm    ${first}[key]    ${move}
    Should Be Equal As Integers    ${response.status_code}    200
    ${view}=    GET Board View As Persona    pm    ${board}
    Should Be Equal    ${view.json()}[columns][1][wipState]    AT
    ${to_backlog}=    Transition Id To    pm    ${second}[key]    BACKLOG
    ${move}=    Create Dictionary    transitionId=${to_backlog}    version=${0}
    ${response}=    POST Transition As Persona    pm    ${second}[key]    ${move}
    Validate Error Body    ${response}    422    GATE_BLOCKED
    Should Be Equal    ${response.json()}[failedRules][0][type]    WIP_LIMIT

Board - Patch Board - PATCH - By HTTP Status Code
    [Documentation]    Executa o cenário de PATCH /boards/{id} correspondente ao status esperado.
    [Arguments]    ${meuHTTP}
    IF    '${meuHTTP}' == '200'
        PATCH Board - Status Code 200
    ELSE IF    '${meuHTTP}' == '403'
        PATCH Board - Status Code 403
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para PATCH /boards/{id}.
    END
