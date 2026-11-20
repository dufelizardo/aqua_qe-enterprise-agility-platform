*** Settings ***
Documentation       Portfolio (UX/UI 7.3 e 6.2): escolhe o caminho e faz as asserções. Usa o fluxo de administração para
...                 criar o Portfólio onde a Iniciativa vai morar.
Resource            ../../pom/portfolio/portfolio_pom.resource
Resource            ../admin/admin_flow.resource

*** Keywords ***
Portfolio - Preparar Portfólio
    [Documentation]    Entra como administrador e cria um Portfólio sob a raiz; devolve o nome.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${name}=    Unique Name    ${portfolio_base}
    Admin - Criar Unidade Sob    ${root_name}    ${name}    ${portfolio_label}
    RETURN    ${name}

Portfolio - Abrir Quadro
    [Documentation]    Abre o Portfolio pelo menu e garante o quadro do workspace (cria se ainda não existe). A trilha
    ...    mostra a unidade ativa (Proposta 06, NAV-008).
    Click Portfolio Nav Link
    Wait For Level Board Page    ${portfolio_level_name}
    Wait For Elements State    ${LEVEL_UNIT_TRAIL}    visible    timeout=30s
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

Tactical - Abrir Quadro
    [Documentation]    Abre o Tactical pelo menu e garante o quadro de FL2 do workspace.
    Click Tactical Nav Link
    Wait For Level Board Page    ${tactical_level_name}
    ${missing}=    Get Create Board Button Count
    IF    ${missing} > 0
        Click Create Board
    END
    Wait For Portfolio Board

Tactical - Criar Feature Sob Iniciativa
    [Documentation]    "Nova Feature" com a Iniciativa como pai e a Cadeia como unidade; a Feature nasce em Backlog.
    [Arguments]    ${title}    ${description}    ${parent_label}    ${cadeia_name}
    Fill New Child Item    ${title}    ${description}    ${parent_label}    ${cadeia_name}
    Submit New Initiative
    Wait For Card In Column    BACKLOG    ${title}
