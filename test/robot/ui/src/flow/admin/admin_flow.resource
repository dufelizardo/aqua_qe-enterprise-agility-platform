*** Settings ***
Documentation       Administração › Estrutura organizacional (UX/UI 7.10): escolhe o caminho e faz as asserções.
Library             String
Resource            ../../pom/common/aquaqe_ui_common.resource
Resource            ../../pom/admin/admin_pom.resource
Resource            ../login/login_flow.resource

*** Keywords ***
Unique Name
    [Documentation]    Nome com sufixo aleatório, para a suíte poder rodar de novo no mesmo banco.
    [Arguments]    ${base}
    ${suffix}=    Generate Random String    6    [LOWER][NUMBERS]
    RETURN    ${base} ${suffix}

Admin - Abrir Estrutura Organizacional
    [Documentation]    Entra como administrador e abre a Administração pelo menu lateral.
    [Arguments]    ${username}    ${display_name}
    Login - Sucesso    ${username}    ${display_name}
    Close Open Menu
    Click Admin Nav Link
    Wait For Organization Tree
    ${url}=    Get Current Page Url
    Should Be Equal    ${url}    ${AQUA_UI_URL}/admin/organizacao

Admin - Criar Unidade Sob
    [Documentation]    Escolhe a unidade pai na árvore e cria uma unidade abaixo dela; a nova unidade aparece na
    ...    árvore e fica selecionada.
    [Arguments]    ${parent_name}    ${name}    ${level_label}
    Select Tree Unit    ${parent_name}
    Create Child Unit    ${name}    ${level_label}
    Wait For Tree Unit    ${name}
    Wait For Detail Name    ${name}
    ${shown}=    Get Detail Name Text
    Should Be Equal    ${shown}    ${name}

Admin - Renomear Unidade
    [Documentation]    Renomeia a unidade escolhida; o novo nome aparece no detalhe e na árvore.
    [Arguments]    ${name}    ${new_name}
    Select Tree Unit    ${name}
    Rename Selected Unit    ${new_name}
    Wait For Detail Name    ${new_name}
    Wait For Tree Unit    ${new_name}

Admin - Vincular Unidade
    [Documentation]    A unidade escolhida passa a atender à unidade de outro ramo (cenário C2, SSD 2.1).
    [Arguments]    ${name}    ${target_name}    ${target_level_label}
    Select Tree Unit    ${name}
    Link Selected Unit To    ${target_name} (${target_level_label})
    Wait For Serves List To Contain    ${target_name}
    ${serves}=    Get Serves List Text
    Should Contain    ${serves}    ${target_name}

Admin - Sem Acesso
    [Documentation]    Quem não é PLATFORM_ADMIN não vê o item Admin e, se abrir o endereço, volta para a home.
    [Arguments]    ${username}    ${display_name}
    Login - Sucesso    ${username}    ${display_name}
    Close Open Menu
    ${count}=    Get Admin Nav Link Count
    Should Be Equal As Integers    ${count}    0
    Go To AQuA-QE Path    /admin/organizacao
    Wait For Home Page
    ${url}=    Get Current Page Url
    Should Start With    ${url}    ${AQUA_UI_URL}/home
