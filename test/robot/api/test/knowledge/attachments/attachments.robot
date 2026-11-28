*** Settings ***
Documentation       Suíte dos anexos dos artefatos (SSD 3.7; PRD-RF-018; #13, REV-08).
Resource            ../../../src/scenario/knowledge/attachments/attachments_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Attachments
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-27
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - REV-08: Validate POST, GET e DELETE Attachment - HTTP 201/200/403/204/404 (ciclo do anexo)
    [Documentation]    O PM envia; o leitor lista e baixa como arquivo, mas não remove; removido, o anexo some.
    [Tags]    POST    GET    DELETE    Attachments    HTTP201    HTTP200    HTTP403    HTTP204    HTTP404    PRD-RF-018    REV-08    smoke
    ATTACHMENTS - UPLOAD LIST DOWNLOAD REMOVE    ${attachments_initiative_title}    ${attachments_file_name}
    ...    ${attachments_file_content}    ${attachments_content_type}

CT-002 - REV-08: Validate POST Attachment - HTTP 403 do leitor e 400 do arquivo vazio
    [Documentation]    Quem só lê não envia; arquivo sem conteúdo é recusado no campo file.
    [Tags]    POST    Attachments    HTTP403    HTTP400    PRD-RF-018    REV-08    security    ASVS-V4
    ATTACHMENTS - READER AND EMPTY FILE    ${attachments_initiative_title}    ${attachments_file_name}
    ...    ${attachments_content_type}
