*** Settings ***
Documentation       Suíte dos documentos da Iniciativa e da Feature (SSD 3.7; PRD-RF-018 a 020; #63).
Resource            ../../../src/scenario/knowledge/documents/documents_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Artifact Documents
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-25
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PRD-RF-018: Validate PUT/POST Artifact Document - HTTP 200 OK (ciclo do PRD)
    [Documentation]    Os cinco documentos nascem pendentes; o PRD é salvo, aprovado com as obrigatórias e reaberto.
    [Tags]    PUT    POST    ArtifactDocuments    HTTP200    PRD-RF-018    PRD-RF-019    PRD-RF-020    smoke
    DOCUMENTS - PRD LIFECYCLE    ${documents_initiative_title}

CT-002 - PRD-RF-018: Validate PUT Artifact Document - HTTP 403 FORBIDDEN
    [Documentation]    Quem só lê não grava documento.
    [Tags]    PUT    ArtifactDocuments    HTTP403    PRD-RF-018    security    ASVS-V4
    DOCUMENTS - READER CANNOT EDIT    ${documents_initiative_title}
