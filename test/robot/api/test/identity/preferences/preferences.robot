*** Settings ***
Documentation       Suíte das Preferências do usuário: tema e avisos do sino (#15; REV-10; UX/UI 7.14).
Resource            ../../../src/scenario/identity/preferences/preferences_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Test Teardown       PREFERENCES - RESET    ${preferences_persona}
Metadata            Suite    Preferências
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-28
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PREF-001: Validate PUT/GET Me Preferences - HTTP 200 e 400 (tema e avisos)
    [Documentation]    Tema escuro e menção desligada são gravados e lidos de volta; tema inválido é 400.
    [Tags]    PUT    GET    Preferences    HTTP200    HTTP400    PREF-001
    PREFERENCES - SAVE AND READ    ${preferences_persona}    ${preferences_theme}
