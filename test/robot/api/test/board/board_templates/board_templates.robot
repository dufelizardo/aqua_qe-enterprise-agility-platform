*** Settings ***
Documentation       Suíte dos templates de quadro: catálogo único, aplicado por cópia (#50; SSD 3.9; MDL-07).
Resource            ../../../src/scenario/board/board_templates/board_templates_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Board Templates
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-27
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - SSD-3.9: Validate POST Board com template e POST Board Template - HTTP 201 (cópia e catálogo)
    [Documentation]    O quadro nasce com as colunas do template pronto; o quadro vira template; o administrador renomeia
    ...    e remove.
    [Tags]    POST    PATCH    DELETE    BoardTemplates    HTTP201    HTTP400    HTTP403    HTTP200    HTTP204    SSD-3.9
    BOARD - TEMPLATE APPLIED BY COPY AND SAVED FROM BOARD    ${board_template_builtin}    ${board_template_columns}
    ...    ${board_template_name}

CT-002 - SSD-3.9: Validate POST Board com template - HTTP 400 (template de outro nível)
    [Documentation]    O template de FL1 não cria quadro de FL3.
    [Tags]    POST    BoardTemplates    HTTP400    SSD-3.9
    BOARD - TEMPLATE OF ANOTHER LEVEL    ${board_template_builtin}
