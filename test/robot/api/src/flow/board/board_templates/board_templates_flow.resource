*** Settings ***
Documentation       Templates de quadro (#50; MDL-07): aplicados por cópia ao criar o quadro; quem gere um quadro salva
...                 as colunas dele como template; o administrador renomeia e remove. Cada caso cria a própria squad.
Library             String
Resource            ../../../pom/board/board_templates/board_templates_pom.resource
Resource            ../../../pom/board/boards/boards_pom.resource
Resource            ../sprints/sprints_flow.resource

*** Keywords ***
Board Template Id
    [Documentation]    Id do template do nível pelo nome exato.
    [Arguments]    ${level}    ${name}
    ${response}=    GET Board Templates As Persona    viewer    ${level}
    Should Be Equal As Integers    ${response.status_code}    200
    ${ids}=    Evaluate    [t['id'] for t in $response.json()['items'] if t['name'] == $name]
    Should Not Be Empty    ${ids}
    RETURN    ${ids}[0]

Board Templates - Criar Quadro Pelo Template E Salvar Como Template
    [Documentation]    O quadro da squad nasce com as colunas do template pronto; o pm salva o quadro como template
    ...    (nome repetido é recusado; o membro da squad não salva); o administrador renomeia e remove, e o quadro
    ...    criado não muda.
    [Arguments]    ${builtin_name}    ${expected_columns}    ${template_base}
    ${squad}=    Create Squad
    ${builtin}=    Board Template Id    FL1    ${builtin_name}
    ${payload}=    Create Dictionary    orgUnitId=${squad}    level=FL1    templateId=${builtin}
    ${board}=    POST Board As Persona    pm    ${payload}
    Should Be Equal As Integers    ${board.status_code}    201
    ${names}=    Evaluate    [c['name'] for c in $board.json()['columns']]
    Should Be Equal    ${names}    ${expected_columns}
    ${suffix}=    Generate Random String    6    [LOWER][NUMBERS]
    ${name}=    Set Variable    ${template_base} ${suffix}
    ${body}=    Create Dictionary    name=${name}
    ${saved}=    POST Board Template From Board As Persona    pm    ${board.json()}[id]    ${body}
    Should Be Equal As Integers    ${saved.status_code}    201
    Should Be Equal    ${saved.json()}[level]    FL1
    Should Not Be True    ${saved.json()}[builtin]
    ${repeated}=    POST Board Template From Board As Persona    pm    ${board.json()}[id]    ${body}
    Validate Error Body    ${repeated}    400    VALIDATION_FAILED
    ${renamed}=    Create Dictionary    name=${name} renomeado
    ${denied}=    PATCH Board Template As Persona    pm    ${saved.json()}[id]    ${renamed}
    Validate Error Body    ${denied}    403    FORBIDDEN
    ${updated}=    PATCH Board Template As Persona    admin    ${saved.json()}[id]    ${renamed}
    Should Be Equal As Integers    ${updated.status_code}    200
    Should Be Equal    ${updated.json()}[name]    ${name} renomeado
    ${removed}=    DELETE Board Template As Persona    admin    ${saved.json()}[id]
    Should Be Equal As Integers    ${removed.status_code}    204

Board Templates - Template De Outro Nivel
    [Documentation]    O template de FL1 não cria quadro de FL3.
    [Arguments]    ${builtin_name}
    ${root}=    Get Root Org Unit Id
    ${portfolio}=    Create Org Unit As Admin    Robot Portfólio Template    PORTFOLIO    ${root}
    ${builtin}=    Board Template Id    FL1    ${builtin_name}
    ${payload}=    Create Dictionary    orgUnitId=${portfolio}    level=FL3    templateId=${builtin}
    ${response}=    POST Board As Persona    pm    ${payload}
    Validate Error Body    ${response}    400    VALIDATION_FAILED
