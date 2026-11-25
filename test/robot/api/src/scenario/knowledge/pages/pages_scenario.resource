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
