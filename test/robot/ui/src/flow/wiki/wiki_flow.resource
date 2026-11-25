*** Settings ***
Documentation       Wiki da unidade (UX/UI 7.12; Proposta 07, Fase A): escolhe o caminho e faz as asserções.
Library             Collections
Resource            ../../pom/common/aquaqe_ui_common.resource
Resource            ../../pom/wiki/wiki_pom.resource
Resource            ../admin/admin_flow.resource
Resource            ../../pom/navigation/navigation_pom.resource
Resource            ../../pom/shell/shell_pom.resource

*** Keywords ***
Wiki - Abrir
    [Documentation]    Entra e escolhe a raiz na árvore: a página da unidade abre na aba Wiki (bug #66).
    [Arguments]    ${username}    ${display_name}    ${root_name}
    Login - Sucesso    ${username}    ${display_name}
    Close Open Menu
    Wait For Nav Org Tree
    Click Org Node    ${root_name}
    Wait For Url To End With    /wiki
    Wait For Wiki Tree
    ${url}=    Get Current Page Url
    Should Start With    ${url}    ${AQUA_UI_URL}/wiki

Wiki - Criar E Editar Pagina
    [Documentation]    A página nova aparece na árvore e abre; escrever no editor contínuo, com negrito pela barra, e
    ...    salvar cria a versão 2 (bug #67); a leitura mostra o texto em negrito; o histórico mostra as duas versões.
    [Arguments]    ${title_base}    ${text}    ${summary}    ${screenshot}
    ${title}=    Unique Name    ${title_base}
    Create Wiki Page    ${title}
    Wait For Wiki Page Title    ${title}
    Wait For Wiki Page Link    ${title}
    Wait For Wiki Page Version    1
    Write Paragraph And Save    ${text}    ${summary}
    Wait For Wiki Page Version    2
    ${content}=    Get Wiki Page Text
    Should Contain    ${content}    ${text}
    ${bold}=    Get Bold Text Count
    Should Be True    ${bold} >= 1
    Take Wiki Screenshot    ${screenshot}
    Open Wiki History
    ${count}=    Get Wiki Version Count
    Should Be Equal As Integers    ${count}    2

Regimento - Preparar Unidade
    [Documentation]    O regimento é um por unidade (bug #66): o administrador cria um Portfólio novo, escolhe-o na
    ...    árvore e abre o Regimento dele, ainda vazio. Devolve o nome da unidade.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${unit_base}    ${unit_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${unit}=    Unique Name    ${unit_base}
    Admin - Criar Unidade Sob    ${root_name}    ${unit}    ${unit_label}
    Regimento - Abrir Da Unidade    ${unit}
    Wait For Regimento Empty
    RETURN    ${unit}

Regimento - Abrir Da Unidade
    [Documentation]    Escolhe a unidade na árvore e abre a função Regimento dela.
    [Arguments]    ${unit}
    Go To AQuA-QE Path    /home
    Wait For Nav Org Tree
    Search Nav Org Tree    ${unit}
    Click Org Node    ${unit}
    Wait For Current Workspace    ${unit}
    Click Current Unit View    nav-regimento
    Wait For Url To End With    /regimento

Regimento - Exige Secoes Obrigatorias
    [Documentation]    O regimento nasce em rascunho e vai para revisão; aprovar sem as seções obrigatórias é recusado,
    ...    com o aviso das seções que faltam; e volta para rascunho.
    [Arguments]    ${title}    ${screenshot}
    Create Regimento    ${title}
    Wait For Wiki Page Title    ${title}
    Wait For Wiki Status    Rascunho
    ${missing}=    Get Wiki Missing Text
    Should Contain    ${missing}    Identificação
    Click Submit For Review
    Wait For Wiki Status    Em revisão
    Click Approve
    Wait For Wiki Error
    ${error}=    Get Wiki Error Text
    Should Contain    ${error}    seções obrigatórias
    Take Wiki Screenshot    ${screenshot}
    Click Back To Draft
    Wait For Wiki Status    Rascunho

Regimento - Sem Aprovacao Para Quem Nao Aprova
    [Documentation]    O administrador deixa o regimento em revisão; o PM, que não é o aprovador (BUSINESS_OWNER),
    ...    vê o status, mas não "Aprovar".
    [Arguments]    ${title}    ${unit}    ${pm_username}    ${pm_display_name}
    Create Regimento    ${title}
    Wait For Wiki Status    Rascunho
    Click Submit For Review
    Wait For Wiki Status    Em revisão
    Open Profile Menu
    Logout - Volta A Landing E Exige Login
    Login - Sucesso    ${pm_username}    ${pm_display_name}
    Close Open Menu
    Regimento - Abrir Da Unidade    ${unit}
    Wait For Wiki Status    Em revisão
    ${count}=    Get Approve Button Count
    Should Be Equal As Integers    ${count}    0

Wiki - Organizar Paginas
    [Documentation]    PROP-002: com duas páginas novas, "Subir" põe a segunda antes da primeira, e "Mover para…" a
    ...    coloca dentro da primeira (nível 2 na árvore).
    [Arguments]    ${title_base}
    ${first}=    Unique Name    ${title_base} 1
    ${second}=    Unique Name    ${title_base} 2
    Create Wiki Page    ${first}
    Wait For Wiki Page Link    ${first}
    Create Wiki Page    ${second}
    Wait For Wiki Page Title    ${second}
    Click Wiki Up
    Wait Until Keyword Succeeds    30s    1s    Wiki - Vem Antes    ${second}    ${first}
    Move Wiki Page Into    ${first}
    Wait For Wiki Tree Level    ${second}    2
    ${level}=    Get Wiki Tree Level    ${first}
    Should Be Equal    ${level}    1

Wiki - Vem Antes
    [Documentation]    Na árvore, o primeiro título aparece antes do segundo.
    [Arguments]    ${earlier}    ${later}
    ${titles}=    Get Wiki Tree Titles
    ${i}=    Get Index From List    ${titles}    ${earlier}
    ${j}=    Get Index From List    ${titles}    ${later}
    Should Be True    0 <= ${i} < ${j}

Wiki - Arrastar Pagina
    [Documentation]    PROP-002: com três páginas novas, arrastar a terceira sobre a primeira a põe antes dela, na raiz.
    [Arguments]    ${title_base}
    ${first}=    Unique Name    ${title_base} A
    ${second}=    Unique Name    ${title_base} B
    ${third}=    Unique Name    ${title_base} C
    Create Wiki Page    ${first}
    Wait For Wiki Page Link    ${first}
    Create Wiki Page    ${second}
    Wait For Wiki Page Link    ${second}
    Create Wiki Page    ${third}
    Wait For Wiki Page Title    ${third}
    Drag Wiki Page Onto    ${third}    ${first}
    Wait Until Keyword Succeeds    30s    1s    Wiki - Vem Antes    ${third}    ${first}
    ${level}=    Get Wiki Tree Level    ${third}
    Should Be Equal    ${level}    1
