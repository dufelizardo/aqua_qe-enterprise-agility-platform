*** Settings ***
Documentation       Suíte de Pessoas e privacidade (#32; REV-14; SSD 2.2).
Resource            ../../../src/scenario/privacy/people/people_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Pessoas e privacidade
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-28
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - REV-14: Validate GET Admin People e Personal Data - HTTP 200 e 403
    [Documentation]    O administrador acha a pessoa e baixa os dados dela; quem não é administrador recebe 403.
    [Tags]    GET    Privacy    HTTP200    HTTP403    REV-14    security    ASVS-V4
    PEOPLE - DIRECTORY AND EXPORT    ${people_persona}    ${people_username_part}

CT-002 - REV-14: Validate POST Anonymize - HTTP 403, 404 e 422
    [Documentation]    Recusas da anonimização: sem papel, a própria conta e pessoa inexistente.
    [Tags]    POST    Privacy    HTTP403    HTTP404    HTTP422    REV-14    security    ASVS-V4
    PEOPLE - ANONYMIZE REFUSED    ${people_persona}    ${people_missing_id}
