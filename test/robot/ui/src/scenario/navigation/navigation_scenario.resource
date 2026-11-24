*** Settings ***
Resource            ../../flow/navigation/navigation_flow.resource
Resource            ../../flow/common/browser_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER ON NAVIGATION
    [Documentation]    Navegador novo, sem cookies nem expansão lembrada, direto em /home.
    Navegador - Abrir    /home

PREREQUISITE: OPEN MOBILE BROWSER ON NAVIGATION
    [Documentation]    Como o anterior, com a tela de um celular.
    Navegador - Abrir Celular    /home

TEARDOWN: CLOSE NAVIGATION BROWSER
    Navegador - Fechar

NAV ORG - ESCOLHER UNIDADE NA ARVORE
    [Documentation]    Monta um ramo, acha a Squad pela busca e a escolhe no menu.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${screenshot}
    ${portfolio}    ${cadeia}    ${squad}=    Navegacao - Montar Ramo    ${username}    ${display_name}
    ...    ${root_name}    ${portfolio_base}    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}
    ...    ${squad_base}    ${squad_label}
    Navegacao - Escolher Unidade Pela Busca    ${portfolio}    ${cadeia}    ${squad}    ${screenshot}

NAV ORG - RECOLHER E LEMBRAR RAMO
    [Documentation]    Monta um ramo, escolhe a Squad e recolhe a Cadeia; a expansão fica lembrada.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${screenshot}
    ${portfolio}    ${cadeia}    ${squad}=    Navegacao - Montar Ramo    ${username}    ${display_name}
    ...    ${root_name}    ${portfolio_base}    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}
    ...    ${squad_base}    ${squad_label}
    Navegacao - Escolher Unidade Pela Busca    ${portfolio}    ${cadeia}    ${squad}    ${screenshot}
    Navegacao - Recolher E Lembrar    ${cadeia}    ${squad}

NAV ORG - GAVETA NO CELULAR
    [Documentation]    O menu com a árvore abre como gaveta no celular.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${screenshot}
    Navegacao - Gaveta No Celular    ${username}    ${display_name}    ${root_name}    ${screenshot}
