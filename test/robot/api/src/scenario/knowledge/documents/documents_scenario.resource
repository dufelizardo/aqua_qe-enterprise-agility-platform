*** Settings ***
Resource            ../../../flow/knowledge/documents/documents_flow.resource

*** Keywords ***
DOCUMENTS - PRD LIFECYCLE
    [Documentation]    Ciclo de um documento da Iniciativa (SSD 3.7).
    [Arguments]    ${title}
    Documents - Ciclo Do PRD    ${title}

DOCUMENTS - READER CANNOT EDIT
    [Documentation]    Leitura sem edição dos documentos (SSD 2 e 3.7).
    [Arguments]    ${title}
    Documents - Leitor Nao Edita    ${title}

DOCUMENTS - EXPORT MARKDOWN
    [Documentation]    Exportação do documento do artefato e da página do Wiki em Markdown (#16).
    [Arguments]    ${title}    ${unit_name}    ${page_title}
    Documents - Exportar Em Markdown    ${title}    ${unit_name}    ${page_title}
