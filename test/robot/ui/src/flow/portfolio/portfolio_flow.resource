*** Settings ***
Documentation       Portfolio (UX/UI 7.3 e 6.2): escolhe o caminho e faz as asserções. Usa o fluxo de administração para
...                 criar o Portfólio onde a Iniciativa vai morar.
Resource            ../../pom/portfolio/portfolio_pom.resource
Resource            ../admin/admin_flow.resource
Resource            ../../pom/navigation/navigation_pom.resource
Resource            ../../pom/artifact/artifact_pom.resource
Resource            ../../pom/gates/gates_pom.resource
Resource            ../../pom/default_owner/default_owner_pom.resource

*** Keywords ***
Portfolio - Preparar Portfólio
    [Documentation]    Entra como administrador e cria um Portfólio sob a raiz; devolve o nome.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    Admin - Abrir Estrutura Organizacional    ${username}    ${display_name}
    ${name}=    Unique Name    ${portfolio_base}
    Admin - Criar Unidade Sob    ${root_name}    ${name}    ${portfolio_label}
    RETURN    ${name}

Squad - Preparar Squad
    [Documentation]    Tudo nasce na Squad (bug #66): o administrador cria Portfólio, Cadeia e Squad; devolve a Squad.
    [Arguments]    ${username}    ${display_name}    ${root_name}    ${portfolio_base}    ${portfolio_label}
    ...    ${cadeia_base}    ${cadeia_label}    ${squad_base}    ${squad_label}
    ${portfolio}=    Portfolio - Preparar Portfólio    ${username}    ${display_name}    ${root_name}
    ...    ${portfolio_base}    ${portfolio_label}
    ${cadeia}=    Unique Name    ${cadeia_base}
    Admin - Criar Unidade Sob    ${portfolio}    ${cadeia}    ${cadeia_label}
    ${squad}=    Unique Name    ${squad_base}
    Admin - Criar Unidade Sob    ${cadeia}    ${squad}    ${squad_label}
    RETURN    ${squad}

Squad - Abrir Quadro Do Nivel
    [Documentation]    Escolhe a Squad na árvore (aplicação recarregada, para a árvore ler as unidades novas), abre o
    ...    Quadro dela e escolhe o nível: portfolio (Iniciativas), tactical (Features) ou squad (Histórias). Garante o
    ...    quadro (cria se ainda não existe). O título da página é o nome da Squad, e a trilha mostra o caminho.
    [Arguments]    ${squad}    ${squad_level_name}    ${board_level}
    Go To AQuA-QE Path    /home
    Wait For Nav Org Tree
    Search Nav Org Tree    ${squad}
    Click Org Node    ${squad}
    Wait For Current Workspace    ${squad}
    Click Current Unit View    nav-squad
    Wait For Level Board Page    ${squad}
    Wait For Unit Trail
    Squad - Trocar Nivel Do Quadro    ${board_level}

Portfolio - Chave Do Cartao
    [Documentation]    Chave do cartão (ex.: INIT-12) pelo status e título, para usar como pai de outro item.
    [Arguments]    ${status}    ${title}
    ${key}=    Get Card Key    ${status}    ${title}
    RETURN    ${key}

Squad - Trocar Nivel Do Quadro
    [Documentation]    O seletor do Quadro da Squad troca o nível do quadro; garante o quadro desse nível.
    [Arguments]    ${board_level}
    Click Board Level    ${board_level}
    Wait For Board Or Empty
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

Tactical - Criar Feature Sob Iniciativa
    [Documentation]    "Nova Feature" com a Iniciativa como pai e a Cadeia como unidade; a Feature nasce em Backlog.
    [Arguments]    ${title}    ${description}    ${parent_label}    ${cadeia_name}
    Fill New Child Item    ${title}    ${description}    ${parent_label}    ${cadeia_name}
    Submit New Initiative
    Wait For Card In Column    BACKLOG    ${title}

Squad - Criar Task
    [Documentation]    "Novo item" do tipo Task, sem pai, na Squad; nasce em A Fazer.
    [Arguments]    ${title}    ${squad_name}
    Fill New Item Without Parent    Task    ${title}    ${squad_name}
    Submit New Initiative
    Wait For Card In Column    TODO    ${title}

Squad - Ciclo Da Sprint
    [Documentation]    Cria a sprint, inclui a Task pelo Backlog, inicia e encerra; o aviso diz que 1 item volta ao
    ...    backlog (UX/UI 7.7).
    [Arguments]    ${squad_name}    ${sprint_name}    ${task_title}
    Open Sprints Tab For Squad    ${squad_name}
    Create Sprint In Tab    ${sprint_name}
    Wait For Sprint State    ${sprint_name}    Planejada
    Include Backlog Item In Sprint    ${task_title}    ${sprint_name}
    Open Sprints Tab For Squad    ${squad_name}
    Click Sprint Action    ${sprint_name}    sprint-start
    Wait For Sprint State    ${sprint_name}    Ativa
    Click Sprint Action    ${sprint_name}    sprint-close
    ${text}=    Get Close Confirmation Text
    Should Contain    ${text}    1 item(ns)
    Confirm Sprint Close
    Wait For Sprint State    ${sprint_name}    Encerrada

Artefato - Documentar E Aprovar PRD
    [Documentation]    #63: o título do cartão abre a página da Iniciativa; na aba PRD, preencher as seções
    ...    obrigatórias e salvar cria a versão 1 (Em andamento); aprovar deixa o PRD Aprovado, também na Visão geral.
    [Arguments]    ${status}    ${title}    ${text}
    ${card}=    Card Locator    ${status}    ${title}
    Open Card Page    ${card}
    Wait For Artifact Title    ${title}
    Click Artifact Tab    PRD
    Wait For Document Title    PRD
    Click Document Edit
    FOR    ${key}    IN    context    objectives    scope    requirements
        Write Document Section    ${key}    ${text}
    END
    Click Document Save
    Wait For Document Version    1
    Wait For Document Status    Em andamento
    Click Document Approve
    Wait For Document Status    Aprovado
    Click Artifact Overview Tab
    Wait For Overview Document Status    PRD    Aprovado

Artefato - Editar Titulo E Responsavel
    [Documentation]    O título do cartão abre a página; clicar no título e no Responsável edita cada campo; ao voltar,
    ...    o cartão mostra o título novo.
    [Arguments]    ${status}    ${title}    ${new_title}    ${person}
    ${card}=    Card Locator    ${status}    ${title}
    Open Card Page    ${card}
    Wait For Artifact Title    ${title}
    Rename Artifact    ${new_title}
    Wait For Artifact Title    ${new_title}
    Choose Artifact Person    ownerId    ${person}
    Wait For Artifact Field    ownerId    ${person}
    Click Artifact Back
    Wait For Card In Column    ${status}    ${new_title}

Artefato - Gate Com Aprovacao
    [Documentation]    #78: o administrador cria um gate com aprovação de Tech Lead na transição Ideia → Cancelado; na
    ...    página da Iniciativa, os Próximos passos mostram a regra pendente e o Mover desabilitado; aprovar libera e
    ...    mover leva a Cancelado. O gate é removido no fim, mesmo se o teste falhar.
    [Arguments]    ${status}    ${title}    ${gate_name}    ${message}    ${path}    ${to_key}    ${to_name}
    ${card}=    Card Locator    ${status}    ${title}
    Open Card Page    ${card}
    Wait For Artifact Title    ${title}
    ${artifact_url}=    Get Current Page Url
    Go To AQuA-QE Path    /admin/gates
    Choose Gates Workflow For Type    INITIATIVE
    Open Gate Of Transition    ${path}
    Fill Approval Gate    ${gate_name}    ${message}
    Save Gate
    Wait Until Keyword Succeeds    10s    500ms    Transition Row Should Contain    ${path}    ${gate_name}
    TRY
        Open Saved Page    ${artifact_url}
        Wait For Artifact Title    ${title}
        ${step}=    Get Gate Step Text    ${to_key}
        Should Contain    ${step}    ${message}
        Should Contain    ${step}    0/1
        ${enabled}=    Is Gate Move Enabled    ${to_key}
        Should Not Be True    ${enabled}
        Approve Gate Rule    ${to_key}
        Wait Until Keyword Succeeds    10s    500ms    Gate Step Should Contain    ${to_key}    1/1
        Click Gate Move    ${to_key}
        Wait Until Keyword Succeeds    15s    500ms    Artifact Status Should Be    ${to_name}
    FINALLY
        Run Keyword And Ignore Error    Remover Gate Da Transicao    ${path}
    END

Responsavel Padrao - Quem Criou
    [Documentation]    #43: em Admin › Responsável padrão, a Squad do teste passa a usar "Quem criou" para Iniciativa;
    ...    o formulário de nova Iniciativa já traz o Owner sugerido; o item nasce com quem criou; "Seguir a de cima"
    ...    devolve a regra herdada.
    [Arguments]    ${squad}    ${squad_level_name}    ${title}    ${description}    ${display_name}
    Go To AQuA-QE Path    /admin/responsavel-padrao
    Choose Default Owner Unit    ${squad}
    Set Default Owner Mode    INITIATIVE    CREATOR
    Wait For Default Owner Summary    INITIATIVE    Quem criou
    ${row}=    Get Default Owner Row Text    INITIATIVE
    Should Contain    ${row}    Desta unidade
    Squad - Abrir Quadro Do Nivel    ${squad}    ${squad_level_name}    portfolio
    Fill New Initiative    ${title}    ${description}    ${squad}
    Wait For New Item Owner
    ${owner}=    Get New Item Owner Label
    Should Contain    ${owner}    ${display_name}
    ${hint}=    Get New Item Owner Hint
    Should Contain    ${hint}    Quem criou
    Submit New Initiative
    Wait For Card In Column    IDEA    ${title}
    ${card}=    Card Locator    IDEA    ${title}
    Open Card Page    ${card}
    Wait For Artifact Title    ${title}
    Wait For Artifact Field    ownerId    ${display_name}
    Go To AQuA-QE Path    /admin/responsavel-padrao
    Choose Default Owner Unit    ${squad}
    Wait For Default Owner Summary    INITIATIVE    Quem criou
    Click Default Owner Inherit    INITIATIVE
    Wait For Default Owner Summary    INITIATIVE    Sem responsável

Remover Gate Da Transicao
    [Documentation]    Limpeza do teste: remove o gate da transição em Admin › Gates.
    [Arguments]    ${path}
    Go To AQuA-QE Path    /admin/gates
    Choose Gates Workflow For Type    INITIATIVE
    Open Gate Of Transition    ${path}
    Remove Gate Accepting

Transition Row Should Contain
    [Arguments]    ${path}    ${text}
    ${row}=    Get Transition Row Text    ${path}
    Should Contain    ${row}    ${text}

Gate Step Should Contain
    [Arguments]    ${to_key}    ${text}
    ${step}=    Get Gate Step Text    ${to_key}
    Should Contain    ${step}    ${text}

Artifact Status Should Be
    [Arguments]    ${name}
    ${status}=    Get Artifact Status Text
    Should Be Equal    ${status}    ${name}
