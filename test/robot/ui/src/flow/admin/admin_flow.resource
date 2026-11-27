*** Settings ***
Documentation       Administração › Estrutura organizacional (UX/UI 7.10): escolhe o caminho e faz as asserções.
Library             String
Resource            ../../pom/common/aquaqe_ui_common.resource
Resource            ../../pom/admin/admin_pom.resource
Resource            ../../pom/workflows/workflows_pom.resource
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

Admin - Desativar Com Confirmacao
    [Documentation]    Desativar pede confirmação e mostra o que ainda está ligado à unidade (#35). Cancelar deixa a
    ...    unidade ativa; confirmar a desativa.
    [Arguments]    ${name}    ${expected_impact}
    Select Tree Unit    ${name}
    Click Toggle Active
    Wait For Deactivation Panel
    ${impact}=    Get Deactivation Impact Text
    Should Contain    ${impact}    ${expected_impact}
    Cancel Deactivation
    Wait For Deactivation Panel Hidden
    ${status}=    Get Detail Status Text
    Should Be Equal    ${status}    ativa
    Click Toggle Active
    Wait For Deactivation Panel
    Confirm Deactivation
    Wait For Detail Status    desativada
    ${status}=    Get Detail Status Text
    Should Be Equal    ${status}    desativada

Admin - Personalizar Workflow Da Unidade
    [Documentation]    #49: na aba Workflows, a unidade personaliza o workflow herdado, acrescenta um status com
    ...    transição, publica a versão 2 e volta a herdar o de cima.
    [Arguments]    ${unit_name}    ${workflow_name}    ${status_name}    ${status_key}    ${from_key}
    Click Workflows Tab
    Wait For Workflows Page
    Choose Workflows Unit    ${unit_name}
    Wait For Workflow Origin    ${workflow_name}    Herdado de
    Click Customize Workflow    ${workflow_name}
    Wait For Workflow Editor
    Wait For Workflow Origin    ${workflow_name}    Desta unidade
    Add Workflow Status    ${status_name}    ${status_key}
    Check Workflow Transition    ${from_key}    ${status_key}
    Publish Workflow Version
    Wait For Workflow Version    ${workflow_name}    v2
    ${version}=    Get Workflow Version Text    ${workflow_name}
    Should Be Equal    ${version}    v2
    Revert Workflow    ${workflow_name}
    Wait For Workflow Origin    ${workflow_name}    Herdado de
    ${origin}=    Get Workflow Origin Text    ${workflow_name}
    Should Contain    ${origin}    Herdado de

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
