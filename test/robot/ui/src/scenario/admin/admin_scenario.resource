*** Settings ***
Resource            ../../flow/admin/admin_flow.resource
Resource            ../../flow/common/browser_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER ON ADMIN
    [Documentation]    Navegador novo, sem cookies, direto em /home (o login leva de volta para lá).
    Navegador - Abrir    /home

TEARDOWN: CLOSE BROWSER
    Navegador - Fechar

ADMIN ORG - CRIAR PORTFOLIO
    [Documentation]    O administrador cria um Portfólio sob a raiz.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${name}=    Unique Name    ${portfolio_base}
    Admin - Criar Unidade Sob    ${root_name}    ${name}    ${portfolio_label}

ADMIN ORG - RENOMEAR UNIDADE
    [Documentation]    O administrador cria e renomeia um Portfólio.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${name}=    Unique Name    ${portfolio_base}
    Admin - Criar Unidade Sob    ${root_name}    ${name}    ${portfolio_label}
    Admin - Renomear Unidade    ${name}    ${name} renomeado

ADMIN ORG - CADEIA COMPARTILHADA
    [Documentation]    Cenário C2: Portfólios A e B, uma Cadeia sob A que passa a atender B.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${portfolio_a}=    Unique Name    ${portfolio_base} A
    ${portfolio_b}=    Unique Name    ${portfolio_base} B
    ${cadeia}=    Unique Name    ${cadeia_base}
    Admin - Criar Unidade Sob    ${root_name}    ${portfolio_a}    ${portfolio_label}
    Admin - Criar Unidade Sob    ${root_name}    ${portfolio_b}    ${portfolio_label}
    Admin - Criar Unidade Sob    ${portfolio_a}    ${cadeia}    ${cadeia_label}
    Admin - Vincular Unidade    ${cadeia}    ${portfolio_b}    ${portfolio_label}

ADMIN ORG - DESATIVAR COM CONFIRMACAO
    [Documentation]    Portfólio com uma Cadeia abaixo: o aviso cita a subunidade; cancela e depois confirma.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${expected_impact}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${portfolio}=    Unique Name    ${portfolio_base}
    ${cadeia}=    Unique Name    ${cadeia_base}
    Admin - Criar Unidade Sob    ${root_name}    ${portfolio}    ${portfolio_label}
    Admin - Criar Unidade Sob    ${portfolio}    ${cadeia}    ${cadeia_label}
    Admin - Desativar Com Confirmacao    ${portfolio}    ${expected_impact}

ADMIN WORKFLOWS - PERSONALIZAR E PUBLICAR
    [Documentation]    Portfólio e Cadeia novos; a Cadeia personaliza o fluxo de Feature, publica a v2 e volta a herdar.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${workflow_name}    ${status_name}    ${status_key}    ${from_key}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${portfolio}=    Unique Name    ${portfolio_base}
    ${cadeia}=    Unique Name    ${cadeia_base}
    Admin - Criar Unidade Sob    ${root_name}    ${portfolio}    ${portfolio_label}
    Admin - Criar Unidade Sob    ${portfolio}    ${cadeia}    ${cadeia_label}
    Admin - Personalizar Workflow Da Unidade    ${cadeia}    ${workflow_name}    ${status_name}    ${status_key}    ${from_key}

ADMIN ORG - SEM ACESSO
    [Documentation]    Quem não administra a plataforma não chega à Administração.
    [Arguments]    ${username}    ${display_name}
    Admin - Sem Acesso    ${username}    ${display_name}

ADMIN PEOPLE - EXPORTAR E CONFIRMAR ANONIMIZACAO
    [Documentation]    Pessoas e privacidade (#32; REV-14): exportar os dados e a confirmação da anonimização.
    [Arguments]    ${username}    ${display_name}    ${search}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    Admin - Pessoas Exportar E Confirmar Anonimizacao    ${search}
