*** Settings ***
Resource            ../../../flow/knowledge/pages/pages_flow.resource

*** Keywords ***
KNOWLEDGE - PAGES
    [Documentation]    Cenário das páginas de conhecimento pelo status esperado.
    [Arguments]    ${meuHTTP}    ${unit_name}    ${title}    ${text}
    Knowledge - Pages - By HTTP Status Code    ${meuHTTP}    ${unit_name}    ${title}    ${text}

KNOWLEDGE - ORGANIZE PAGES
    [Documentation]    Cenário de reorganização da árvore do Wiki (PROP-002).
    [Arguments]    ${unit_name}    ${title}
    Pages - Reorganizar    ${unit_name}    ${title}

KNOWLEDGE - COPY PAGE
    [Documentation]    Cenário de POST /api/v1/pages/{id}/copy (menu ⋯ do Wiki).
    [Arguments]    ${unit_name}    ${title}    ${text}
    Pages - Fazer Uma Copia    ${unit_name}    ${title}    ${text}

KNOWLEDGE - COMMENTS
    [Documentation]    Cenário de comentários, @menção, notificações e favoritos (#85).
    [Arguments]    ${unit_name}    ${title}    ${comment_text}    ${quote}
    Pages - Comentar Mencionar E Favoritar    ${unit_name}    ${title}    ${comment_text}    ${quote}

KNOWLEDGE - EDITOR EXTRAS
    [Documentation]    Cenário do ícone da página, da lista de tarefas e das cores (#86).
    [Arguments]    ${unit_name}    ${title}    ${icon}    ${color}
    Pages - Icone Tarefas E Cores    ${unit_name}    ${title}    ${icon}    ${color}
