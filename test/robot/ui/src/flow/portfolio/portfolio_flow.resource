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
    [Documentation]    Abre o Portfolio pelo menu e garante o quadro do workspace (cria se ainda não existe).
    Click Portfolio Nav Link
    Wait For Portfolio Page
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
