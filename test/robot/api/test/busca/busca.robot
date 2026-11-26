*** Settings ***
Documentation       Suíte da busca: GET /search em artefatos, documentos e páginas (#84; SSD 3.10; ADR-11).
Resource            ../../src/scenario/busca/busca_scenario.resource
Resource            ../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Search
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-26
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - KNOW-014: Validate GET Search - HTTP 200 OK (artefato e página, sem acento)
    [Documentation]    O artefato é achado pelo título sem acento; a página, pelo texto salvo, com o trecho; kind filtra.
    [Tags]    GET    Search    HTTP200    KNOW-014    smoke
    SEARCH - FIND    ${search_unit_name}    ${search_artifact_title}    ${search_page_title}    ${search_page_text}

CT-002 - KNOW-014: Validate GET Search - HTTP 400 BAD REQUEST
    [Documentation]    Menos de 2 caracteres ou tipo desconhecido.
    [Tags]    GET    Search    HTTP400    KNOW-014
    SEARCH - VALIDATION
