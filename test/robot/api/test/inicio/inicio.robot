*** Settings ***
Documentation       Suíte da Home: "Continuar de onde parou" e "Novidades" (#83; UX/UI 7.2).
Resource            ../../src/scenario/inicio/inicio_scenario.resource
Resource            ../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Home
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-25
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - HOME-001: Validate POST/GET Me Recent - HTTP 204 e 200 (continuar de onde parou)
    [Documentation]    Visitas a artefato, documento e página voltam da mais nova para a mais antiga; tipo inválido é 400.
    [Tags]    POST    GET    Recent    HTTP204    HTTP200    HTTP400    HOME-001
    HOME - RECENT    ${home_unit_name}    ${home_page_title}    ${home_artifact_title}

CT-002 - HOME-002: Validate GET Me News - HTTP 200 OK (novidades das unidades que a pessoa lê)
    [Documentation]    O artefato criado pelo PM aparece nas novidades do leitor; para o PM, marcado como próprio.
    [Tags]    GET    News    HTTP200    HOME-002
    HOME - NEWS    ${home_unit_name}    ${home_news_title}
