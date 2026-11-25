*** Settings ***
Resource            ../../flow/wiki/wiki_flow.resource
Resource            ../../flow/common/browser_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER ON WIKI
    [Documentation]    Navegador novo, sem cookies, direto em /home (o login leva de volta para lá).
    Navegador - Abrir    /home

TEARDOWN: CLOSE WIKI BROWSER
    Navegador - Fechar

WIKI UI - CRIAR E EDITAR PAGINA
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${title_base}    ${text}    ${summary}
    ...    ${screenshot}
    Wiki - Abrir    ${username}    ${display_name}    ${root_name}
    Wiki - Criar E Editar Pagina    ${title_base}    ${text}    ${summary}    ${screenshot}

WIKI UI - REGIMENTO EXIGE SECOES
    [Documentation]    Regimento independente do Wiki, um por unidade: numa unidade nova, o administrador (que também
    ...    aprova) cria o regimento e não consegue aprová-lo sem as seções obrigatórias.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${unit_base}    ${unit_label}    ${title_base}
    ...    ${screenshot}
    ${unit}=    Regimento - Preparar Unidade    ${username}    ${display_name}    ${root_name}    ${unit_base}
    ...    ${unit_label}
    ${title}=    Unique Name    ${title_base}
    Regimento - Exige Secoes Obrigatorias    ${title}    ${screenshot}

WIKI UI - SEM APROVACAO PARA O PM
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${unit_base}    ${unit_label}    ${title_base}
    ...    ${pm_username}    ${pm_display_name}
    ${unit}=    Regimento - Preparar Unidade    ${username}    ${display_name}    ${root_name}    ${unit_base}
    ...    ${unit_label}
    ${title}=    Unique Name    ${title_base}
    Regimento - Sem Aprovacao Para Quem Nao Aprova    ${title}    ${unit}    ${pm_username}    ${pm_display_name}

WIKI UI - ORGANIZAR PAGINAS
    [Documentation]    Reorganizar a árvore do Wiki (PROP-002).
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${title_base}
    Wiki - Abrir    ${username}    ${display_name}    ${root_name}
    Wiki - Organizar Paginas    ${title_base}

WIKI UI - ARRASTAR PAGINA
    [Documentation]    Arrastar e soltar na árvore do Wiki (PROP-002).
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${title_base}
    Wiki - Abrir    ${username}    ${display_name}    ${root_name}
    Wiki - Arrastar Pagina    ${title_base}

WIKI UI - MENU DA PAGINA
    [Documentation]    Menu ⋯ da página do Wiki: renomear, fazer uma cópia e arquivar.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${title_base}    ${renamed_suffix}
    Wiki - Abrir    ${username}    ${display_name}    ${root_name}
    Wiki - Menu Da Pagina    ${title_base}    ${renamed_suffix}
