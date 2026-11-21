*** Settings ***
Documentation       Menu lateral com a árvore organizacional (Proposta 06, Revisão 2; PROPOSAL-07): escolhe o caminho
...                 e faz as asserções.
Resource            ../../pom/common/aquaqe_ui_common.resource
Resource            ../../pom/navigation/navigation_pom.resource
Resource            ../../pom/shell/shell_pom.resource
Resource            ../admin/admin_flow.resource

*** Keywords ***
Navegacao - Montar Ramo
    [Documentation]    O administrador cria Portfólio › Cadeia › Squad pela Administração e volta à home com a
    ...    aplicação recarregada (a árvore do menu é lida na carga). Devolve os três nomes.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${portfolio}=    Unique Name    ${portfolio_base}
    ${cadeia}=    Unique Name    ${cadeia_base}
    ${squad}=    Unique Name    ${squad_base}
    Admin - Criar Unidade Sob    ${root_name}    ${portfolio}    ${portfolio_label}
    Admin - Criar Unidade Sob    ${portfolio}    ${cadeia}    ${cadeia_label}
    Admin - Criar Unidade Sob    ${cadeia}    ${squad}    ${squad_label}
    Go To AQuA-QE Path    /home
    Wait For Home Page
    Wait For Nav Org Tree
    RETURN    ${portfolio}    ${cadeia}    ${squad}

Navegacao - Escolher Unidade Pela Busca
    [Documentation]    A busca deixa a unidade e o caminho até ela; escolher a Squad troca o workspace e leva à
    ...    tela do Flight Level dela (FL1, /squad).
    [Arguments]    ${portfolio}    ${cadeia}    ${squad}    ${screenshot}
    Search Nav Org Tree    ${squad}
    Wait For Org Node    ${squad}
    Wait For Org Node    ${cadeia}
    Wait For Org Node    ${portfolio}
    Click Org Node    ${squad}
    Wait For Url To End With    /squad
    Wait For Current Workspace    ${squad}
    ${current}=    Get Current Workspace Text
    Should Be Equal    ${current}    ${squad}
    Navegacao - Menu Da Squad    ${squad}
    Take Navigation Screenshot    ${screenshot}

Navegacao - Recolher E Lembrar
    [Documentation]    Sem busca, o caminho até a unidade ativa está aberto; recolher a Cadeia esconde a Squad, e
    ...    a escolha continua depois de recarregar a página (ORG-NAV-003 e 015).
    [Arguments]    ${cadeia}    ${squad}
    Clear Nav Org Tree Search
    Wait For Org Node    ${squad}
    Click Org Node Toggle    ${cadeia}
    Wait For Org Node Hidden    ${squad}
    Go To AQuA-QE Path    /home
    Wait For Nav Org Tree
    Wait For Org Node    ${cadeia}
    ${count}=    Get Org Node Count    ${squad}
    Should Be Equal As Integers    ${count}    0
    Click Org Node Toggle    ${cadeia}
    Wait For Org Node    ${squad}

Navegacao - Gaveta No Celular
    [Documentation]    No celular o menu fica escondido; o botão do menu abre a gaveta com a árvore, e escolher
    ...    uma unidade fecha a gaveta (ORG-NAV-014).
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${screenshot}
    Login - Sucesso    ${username}    ${display_name}
    Close Open Menu
    ${open}=    Get Sidebar Visible
    Should Not Be True    ${open}
    Click Menu Toggle
    Wait For Nav Org Tree
    Wait For Org Node    ${root_name}
    Take Navigation Screenshot    ${screenshot}
    Click Org Node    ${root_name}
    Wait For Current Workspace    ${root_name}
    Wait For Sidebar Hidden

Navegacao - Menu Da Squad
    [Documentation]    Bug #66: com a Squad escolhida, as funções dela aparecem dentro da árvore, sob a própria Squad
    ...    (Quadro, Backlog, Sprints e Wiki), sem as de cima; o item Backlog abre a aba Backlog.
    [Arguments]    ${squad}
    ${labels}=    Get Unit View Labels    ${squad}
    ${expected}=    Create List    Quadro    Backlog    Sprints    Wiki
    Should Be Equal    ${labels}    ${expected}
    ${count}=    Get View Count    ${NAV_VIEW_PORTFOLIO}
    Should Be Equal As Integers    ${count}    0
    ${count}=    Get View Count    ${NAV_VIEW_TACTICAL}
    Should Be Equal As Integers    ${count}    0
    ${count}=    Get View Count    ${NAV_VIEW_SQUAD_SPRINTS}
    Should Be Equal As Integers    ${count}    1
    Click Squad Backlog View
    Wait For Squad Backlog Tab
    Wait For Url To End With    /squad?tab=backlog
