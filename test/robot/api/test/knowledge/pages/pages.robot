*** Settings ***
Documentation       Suíte das páginas de conhecimento por unidade (SSD 3.7.1; Proposta 07, Fase A; KNOW-001 a 018).
Resource            ../../../src/scenario/knowledge/pages/pages_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Knowledge Pages
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - KNOW-006: Validate POST/PUT Pages - HTTP 201 CREATED (página, subpágina, versão e conflito)
    [Documentation]    Página livre publica ao salvar; a subpágina fica sob ela; gravar sobre versão antiga dá 409.
    [Tags]    POST    PUT    Pages    HTTP201    KNOW-006    KNOW-007    smoke
    KNOWLEDGE - PAGES    201    ${knowledge_unit_name}    ${knowledge_page_title}    ${knowledge_text}

CT-002 - KNOW-009: Validate POST Page Status - HTTP 200 OK (regimento: revisão, aprovação e publicação)
    [Documentation]    O PM não aprova; o BO aprova só com as seções obrigatórias e publica em passo separado.
    [Tags]    POST    PageStatus    HTTP200    KNOW-009    KNOW-018    KNOW-028
    KNOWLEDGE - PAGES    200    ${knowledge_unit_name}    ${knowledge_regimento_title}    ${knowledge_text}

CT-003 - KNOW-012: Validate POST Pages - HTTP 403 FORBIDDEN
    [Documentation]    Quem só lê não cria página.
    [Tags]    POST    Pages    HTTP403    KNOW-012    security    ASVS-V4
    KNOWLEDGE - PAGES    403    ${knowledge_unit_name}    ${knowledge_page_title}    ${knowledge_text}

CT-004 - KNOW-006: Validate POST Page Rank - HTTP 200 OK (reordenar e mover sem versão nova)
    [Documentation]    PROP-002: reordenar irmãs, mover para dentro de outra página e recusar ciclo.
    [Tags]    POST    PageRank    HTTP200    KNOW-006    PROP-002
    KNOWLEDGE - ORGANIZE PAGES    ${knowledge_unit_name}    ${knowledge_page_title}

CT-005 - KNOW-006: Validate POST Page Copy - HTTP 201 CREATED (Fazer uma cópia)
    [Documentation]    Menu ⋯: a cópia fica logo depois da original com o conteúdo atual; quem só lê recebe 403.
    [Tags]    POST    PageCopy    HTTP201    HTTP403    KNOW-006
    KNOWLEDGE - COPY PAGE    ${knowledge_unit_name}    ${knowledge_page_title}    ${knowledge_text}

CT-006 - KNOW-024: Validate POST Page Comments - HTTP 201 CREATED (@menção, notificação e favorito)
    [Documentation]    #85: comentário com trecho e menção; o mencionado recebe a notificação; a estrela vai para os favoritos.
    [Tags]    POST    PageComments    Notifications    Favorites    HTTP201    HTTP400    KNOW-024    KNOW-025
    KNOWLEDGE - COMMENTS    ${knowledge_unit_name}    ${knowledge_page_title}    ${knowledge_comment_text}
    ...    ${knowledge_comment_quote}

CT-007 - KNOW-020: Validate PATCH Page Icon e PUT com tarefas e cores - HTTP 200 OK
    [Documentation]    #86: ícone sem versão nova; lista de tarefas e cor #rrggbb gravadas; ícone e cor inválidos dão 400.
    [Tags]    PATCH    PUT    Pages    HTTP200    HTTP400    KNOW-020
    KNOWLEDGE - EDITOR EXTRAS    ${knowledge_unit_name}    ${knowledge_page_title}    ${knowledge_icon}    ${knowledge_color}
