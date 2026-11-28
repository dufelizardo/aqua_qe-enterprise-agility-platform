*** Settings ***
Documentation       Preferências do usuário: tema e avisos do sino (#15; REV-10; UX/UI 7.14).
Resource            ../../src/scenario/preferences/preferences_scenario.resource
Resource            ../../src/resource/data_driven/preferences/preferences_driven.resource
Test Teardown       TEARDOWN: CLOSE PREFERENCES BROWSER
Metadata            Suite    Preferências
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-28
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - PREF-002: Validate Preferências - tema escuro e menção desligada ficam gravados
    [Documentation]    Pelo menu do avatar, escolher o tema escuro muda a tela na hora e depois de recarregar; a menção
    ...    desligada também fica gravada. No fim, volta aos padrões.
    [Tags]    UI    Preferences    PREF-002    REV-10
    [Setup]    PREREQUISITE: OPEN BROWSER FOR PREFERENCES
    PREFERENCES - TEMA E AVISOS    ${pref_username}    ${pref_display_name}
