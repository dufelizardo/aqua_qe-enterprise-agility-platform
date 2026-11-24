*** Settings ***
Documentation       Portfolio (UX/UI 7.3 e 6.2): escolhe o caminho e faz as asserções. Usa o fluxo de administração para
...                 criar o Portfólio onde a Iniciativa vai morar.
Resource            ../../pom/portfolio/portfolio_pom.resource
Resource            ../admin/admin_flow.resource
Resource            ../../pom/navigation/navigation_pom.resource

*** Keywords ***
Portfolio - Preparar Portfólio
    [Documentation]    Entra como administrador e cria um Portfólio sob a raiz; devolve o nome.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${name}=    Unique Name    ${portfolio_base}
    Admin - Criar Unidade Sob    ${root_name}    ${name}    ${portfolio_label}
    RETURN    ${name}

Squad - Preparar Squad
    [Documentation]    Tudo nasce na Squad (bug #66): o administrador cria Portfólio, Cadeia e Squad; devolve a Squad.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${portfolio}=    Portfolio - Preparar Portfólio    ${username}    ${display_name}    ${root_name}
    ...    ${portfolio_base}    ${portfolio_label}
    ${cadeia}=    Unique Name    ${cadeia_base}
    Admin - Criar Unidade Sob    ${portfolio}    ${cadeia}    ${cadeia_label}
    ${squad}=    Unique Name    ${squad_base}
    Admin - Criar Unidade Sob    ${cadeia}    ${squad}    ${squad_label}
    RETURN    ${squad}

Squad - Abrir Quadro Do Nivel
    [Documentation]    Escolhe a Squad na árvore (aplicação recarregada, para a árvore ler as unidades novas), abre o
    ...    Quadro dela e escolhe o nível: portfolio (Iniciativas), tactical (Features) ou squad (Histórias). Garante o
    ...    quadro (cria se ainda não existe). O título da página é o nome da Squad, e a trilha mostra o caminho.
    [Arguments]    ${squad}    ${squad_level_name}    ${board_level}
    Go To AQuA-QE Path    /home
    Wait For Nav Org Tree
    Search Nav Org Tree    ${squad}
    Click Org Node    ${squad}
    Wait For Current Workspace    ${squad}
    Click Current Unit View    nav-squad
    Wait For Level Board Page    ${squad}
    Wait For Unit Trail
    Squad - Trocar Nivel Do Quadro    ${board_level}

Portfolio - Chave Do Cartao
    [Documentation]    Chave do cartão (ex.: INIT-12) pelo status e título, para usar como pai de outro item.
    [Arguments]    ${status}    ${title}
    ${key}=    Get Card Key    ${status}    ${title}
    RETURN    ${key}

Squad - Trocar Nivel Do Quadro
    [Documentation]    O seletor do Quadro da Squad troca o nível do quadro; garante o quadro desse nível.
    [Arguments]    ${board_level}
    Click Board Level    ${board_level}
    Wait For Board Or Empty
    ${missing}=    Get Create Board Button Count
    IF    ${missing} > 0
        Click Create Board
    END
    Wait For Portfolio Board

Portfolio - Criar Iniciativa
    [Documentation]    "Nova Iniciativa" cria o item, que aparece na coluna Ideia, o primeiro status do fluxo.
    [Arguments]    ${title}    ${description}    ${unit_name}
    Fill New Initiative    ${title}    ${description}    ${unit_name}
    Submit New Initiative
    Wait For Card In Column    IDEA    ${title}

Portfolio - Mover Iniciativa
    [Documentation]    "Mover para…" executa a transição; o cartão sai da coluna de origem e aparece na de destino.
    [Arguments]    ${title}    ${from_status}    ${to_status}
    Move Card Using Menu    ${from_status}    ${title}    ${to_status}
    Wait For Card In Column    ${to_status}    ${title}
    ${left}=    Get Card Count In Column    ${from_status}    ${title}
    Should Be Equal As Integers    ${left}    0

Portfolio - Lista Mostra A Iniciativa
    [Arguments]    ${title}
    Show List
    ${text}=    Get List Text
    Should Contain    ${text}    ${title}

Portfolio - Configurar Limite De WIP
    [Documentation]    Define o limite de WIP da primeira coluna (Ideia) e confere o contador "n / limite"; depois
    ...    volta a deixar sem limite, porque o quadro do workspace é compartilhado com as demais suítes.
    [Arguments]    ${limit}
    Open Board Configuration
    Set First Column WIP Limit    ${limit}
    Save Board Configuration
    Wait For Idea Column Counter    contains    / ${limit}
    ${counter}=    Get Idea Column Counter Text
    Should End With    ${counter}    / ${limit}
    Open Board Configuration
    Set First Column WIP Limit    ${EMPTY}
    Save Board Configuration
    Wait For Idea Column Counter    not contains    /
    ${counter}=    Get Idea Column Counter Text
    Should Not Contain    ${counter}    /

Portfolio - Bloquear E Desbloquear Iniciativa
    [Documentation]    Marca a Iniciativa como bloqueada (o cartão mostra a etiqueta) e desbloqueia.
    [Arguments]    ${title}    ${reason}
    Block Card Using Menu    IDEA    ${title}    ${reason}
    Wait For Card Blocked State    IDEA    ${title}    visible
    Unblock Card Using Menu    IDEA    ${title}
    Wait For Card Blocked State    IDEA    ${title}    detached

Tactical - Criar Feature Sob Iniciativa
    [Documentation]    "Nova Feature" com a Iniciativa como pai e a Cadeia como unidade; a Feature nasce em Backlog.
    [Arguments]    ${title}    ${description}    ${parent_label}    ${cadeia_name}
    Fill New Child Item    ${title}    ${description}    ${parent_label}    ${cadeia_name}
    Submit New Initiative
    Wait For Card In Column    BACKLOG    ${title}

Squad - Criar Task
    [Documentation]    "Novo item" do tipo Task, sem pai, na Squad; nasce em A Fazer.
    [Arguments]    ${title}    ${squad_name}
    Fill New Item Without Parent    Task    ${title}    ${squad_name}
    Submit New Initiative
    Wait For Card In Column    TODO    ${title}

Squad - Ciclo Da Sprint
    [Documentation]    Cria a sprint, inclui a Task pelo Backlog, inicia e encerra; o aviso diz que 1 item volta ao
    ...    backlog (UX/UI 7.7).
    [Arguments]    ${squad_name}    ${sprint_name}    ${task_title}
    Open Sprints Tab For Squad    ${squad_name}
    Create Sprint In Tab    ${sprint_name}
    Wait For Sprint State    ${sprint_name}    Planejada
    Include Backlog Item In Sprint    ${task_title}    ${sprint_name}
    Open Sprints Tab For Squad    ${squad_name}
    Click Sprint Action    ${sprint_name}    sprint-start
    Wait For Sprint State    ${sprint_name}    Ativa
    Click Sprint Action    ${sprint_name}    sprint-close
    ${text}=    Get Close Confirmation Text
    Should Contain    ${text}    1 item(ns)
    Confirm Sprint Close
    Wait For Sprint State    ${sprint_name}    Encerrada
