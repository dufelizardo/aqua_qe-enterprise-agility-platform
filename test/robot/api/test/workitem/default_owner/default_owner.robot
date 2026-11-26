*** Settings ***
Documentation       Suíte do responsável padrão por unidade e tipo de artefato (#43; SSD 3.4).
Resource            ../../../src/scenario/workitem/default_owner/default_owner_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Default Owner
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-26
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - SSD-3.4: Validate PUT Default Owner Rule e POST Artifact - HTTP 200 (quem criou fica como Owner)
    [Documentation]    Só o administrador grava; o item sem ownerId nasce com o criador; remover a regra volta a herdar.
    [Tags]    PUT    POST    DELETE    DefaultOwner    HTTP403    HTTP200    HTTP204    SSD-3.4
    WORKITEM - DEFAULT OWNER FROM CREATOR    ${default_owner_initiative_title}

CT-002 - SSD-3.4: Validate PUT Default Owner Rule - HTTP 400 (papel ausente ou administrador)
    [Documentation]    A regra por papel exige um papel, e o administrador da plataforma não é opção.
    [Tags]    PUT    DefaultOwner    HTTP400    SSD-3.4
    WORKITEM - DEFAULT OWNER INVALID RULE
