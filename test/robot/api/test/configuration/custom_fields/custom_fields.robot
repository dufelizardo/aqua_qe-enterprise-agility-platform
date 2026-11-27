*** Settings ***
Documentation       Suíte dos campos customizados por tipo de artefato (#17; REV-12; SSD 3.3 e 5.2).
Resource            ../../../src/scenario/configuration/custom_fields/custom_fields_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Campos customizados
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-27
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - SSD-3.3: Validate POST, PATCH e DELETE Custom Field e PATCH Artifact - HTTP 201/400/204 (só campos declarados)
    [Documentation]    Só o administrador declara; a chave e o tipo não mudam; o item aceita só o campo declarado e uma
    ...    das opções; a remoção tira o campo do catálogo.
    [Tags]    POST    PATCH    DELETE    CustomFields    HTTP403    HTTP201    HTTP400    HTTP200    HTTP204    SSD-3.3    REV-12
    CONFIGURATION - CUSTOM FIELD DECLARE WRITE REMOVE    ${custom_field_type}    ${custom_field_key_base}
    ...    ${custom_field_label}    ${custom_field_options}    ${custom_field_valid_option}
    ...    ${custom_field_invalid_option}    ${custom_field_title}

CT-002 - SSD-5.2: Validate PUT Gate - HTTP 400 (campo customizado não declarado)
    [Documentation]    O gate não cita campo customizado que não está declarado em nenhum tipo do workflow.
    [Tags]    PUT    CustomFields    Gates    HTTP400    SSD-5.2    REV-12
    CONFIGURATION - CUSTOM FIELD UNDECLARED IN GATE    ${custom_field_type}    ${custom_field_key_base}
    ...    ${custom_field_gate_title}
