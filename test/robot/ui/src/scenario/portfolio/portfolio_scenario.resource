*** Settings ***
Resource            ../../flow/portfolio/portfolio_flow.resource
Resource            ../../flow/common/browser_flow.resource

*** Keywords ***
PREREQUISITE: OPEN BROWSER FOR PORTFOLIO
    [Documentation]    Navegador novo, sem cookies, direto em /home (o login leva de volta para lá).
    Navegador - Abrir    /home

TEARDOWN: CLOSE PORTFOLIO BROWSER
    Navegador - Fechar

PORTFOLIO - CRIAR E MOVER INICIATIVA
    [Documentation]    Tudo nasce na Squad (bug #66): monta Portfólio, Cadeia e Squad, abre o Quadro da Squad em
    ...    Iniciativas, cria a Iniciativa e a move de Ideia para Backlog.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${squad_level_name}
    ...    ${initiative_base}    ${initiative_description}
    ${squad}=    Squad - Preparar Squad    ${username}    ${display_name}    ${root_name}    ${portfolio_base}
    ...    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${title}=    Unique Name    ${initiative_base}
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    portfolio
    Portfolio - Criar Iniciativa    ${title}    ${initiative_description}    ${squad}
    Portfolio - Mover Iniciativa    ${title}    IDEA    BACKLOG
    Portfolio - Lista Mostra A Iniciativa    ${title}

PORTFOLIO - CONFIGURAR WIP E BLOQUEAR
    [Documentation]    Configura o limite de WIP da coluna Ideia e marca e desmarca uma Iniciativa como bloqueada, no
    ...    Quadro da Squad em Iniciativas.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${squad_level_name}
    ...    ${initiative_base}    ${initiative_description}    ${wip_limit}    ${block_reason}
    ${squad}=    Squad - Preparar Squad    ${username}    ${display_name}    ${root_name}    ${portfolio_base}
    ...    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${title}=    Unique Name    ${initiative_base}
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    portfolio
    Portfolio - Criar Iniciativa    ${title}    ${initiative_description}    ${squad}
    Portfolio - Configurar Limite De WIP    ${wip_limit}
    Portfolio - Bloquear E Desbloquear Iniciativa    ${title}    ${block_reason}

TACTICAL - CRIAR E MOVER FEATURE
    [Documentation]    Na mesma Squad: Iniciativa em Iniciativas; Feature sob ela em Features, movida de Backlog para
    ...    Em Refinamento.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${squad_level_name}
    ...    ${initiative_base}    ${feature_base}    ${description}
    ${squad}=    Squad - Preparar Squad    ${username}    ${display_name}    ${root_name}    ${portfolio_base}
    ...    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${initiative}=    Unique Name    ${initiative_base}
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    portfolio
    Portfolio - Criar Iniciativa    ${initiative}    ${description}    ${squad}
    ${key}=    Portfolio - Chave Do Cartao    IDEA    ${initiative}
    ${feature}=    Unique Name    ${feature_base}
    Squad - Trocar Nivel Do Quadro    tactical
    Tactical - Criar Feature Sob Iniciativa    ${feature}    ${description}    ${key} · ${initiative}    ${squad}
    Portfolio - Mover Iniciativa    ${feature}    BACKLOG    REFINEMENT

SQUAD - SPRINT COMPLETA
    [Documentation]    Task na Squad (Quadro em Histórias); sprint criada, com a Task, iniciada e encerrada.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${squad_level_name}    ${task_base}
    ...    ${sprint_base}
    ${squad}=    Squad - Preparar Squad    ${username}    ${display_name}    ${root_name}    ${portfolio_base}
    ...    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${task}=    Unique Name    ${task_base}
    ${sprint}=    Unique Name    ${sprint_base}
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    squad
    Squad - Criar Task    ${task}    ${squad}
    Squad - Ciclo Da Sprint    ${squad}    ${sprint}    ${task}

PORTFOLIO - DOCUMENTOS DA INICIATIVA
    [Documentation]    #63: cria a Iniciativa no Quadro da Squad, abre a página dela pelo cartão e documenta e aprova
    ...    o PRD.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${squad_level_name}
    ...    ${initiative_base}    ${initiative_description}    ${document_text}
    ${squad}=    Squad - Preparar Squad    ${username}    ${display_name}    ${root_name}    ${portfolio_base}
    ...    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${title}=    Unique Name    ${initiative_base}
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    portfolio
    Portfolio - Criar Iniciativa    ${title}    ${initiative_description}    ${squad}
    Artefato - Documentar E Aprovar PRD    IDEA    ${title}    ${document_text}

PORTFOLIO - EDITAR DADOS DA INICIATIVA
    [Documentation]    Cria a Iniciativa no Quadro da Squad, abre a página dela pelo cartão e edita o título e o
    ...    Responsável clicando nos campos.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${squad_level_name}
    ...    ${initiative_base}    ${initiative_description}    ${renamed_suffix}
    ${squad}=    Squad - Preparar Squad    ${username}    ${display_name}    ${root_name}    ${portfolio_base}
    ...    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${title}=    Unique Name    ${initiative_base}
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    portfolio
    Portfolio - Criar Iniciativa    ${title}    ${initiative_description}    ${squad}
    Artefato - Editar Titulo E Responsavel    IDEA    ${title}    ${title} ${renamed_suffix}    ${display_name}

PORTFOLIO - GATE COM APROVACAO
    [Documentation]    Cria a Iniciativa no Quadro da Squad e testa o gate com aprovação nos Próximos passos (#78).
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}    ${squad_level_name}
    ...    ${initiative_base}    ${initiative_description}    ${gate_name}    ${gate_message}    ${gate_path}
    ...    ${gate_to_key}    ${gate_to_name}
    ${squad}=    Squad - Preparar Squad    ${username}    ${display_name}    ${root_name}    ${portfolio_base}
    ...    ${portfolio_label}    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${title}=    Unique Name    ${initiative_base}
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    portfolio
    Portfolio - Criar Iniciativa    ${title}    ${initiative_description}    ${squad}
    Artefato - Gate Com Aprovacao    IDEA    ${title}    ${gate_name}    ${gate_message}    ${gate_path}    ${gate_to_key}
    ...    ${gate_to_name}
