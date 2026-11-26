*** Settings ***
Resource            ../../flow/busca/busca_flow.resource

*** Keywords ***
SEARCH - FIND
    [Documentation]    Cenário de GET /api/v1/search com resultados.
    [Arguments]    ${unit_name}    ${artifact_title}    ${page_title}    ${page_text}
    Busca - Artefato E Pagina    ${unit_name}    ${artifact_title}    ${page_title}    ${page_text}

SEARCH - VALIDATION
    [Documentation]    Cenário de GET /api/v1/search inválido.
    Busca - Validacao
