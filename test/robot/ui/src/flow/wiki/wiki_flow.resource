*** Settings ***
Documentation       Wiki da unidade (UX/UI 7.12; Proposta 07, Fase A): escolhe o caminho e faz as asserções.
Resource            ../../pom/common/aquaqe_ui_common.resource
Resource            ../../pom/wiki/wiki_pom.resource
Resource            ../admin/admin_flow.resource

*** Keywords ***
Wiki - Abrir
    [Documentation]    Entra e abre o Wiki da unidade ativa pelo menu lateral.
    [Arguments]    ${username}    ${display_name}
    Login - Sucesso    ${username}    ${display_name}
    Close Open Menu
    Click Wiki Nav Link
    Wait For Wiki Tree
    ${url}=    Get Current Page Url
    Should Start With    ${url}    ${AQUA_UI_URL}/wiki

Wiki - Criar E Editar Pagina
    [Documentation]    A página nova aparece na árvore e abre; salvar um parágrafo cria a versão 2, e o histórico
    ...    mostra as duas versões.
    [Arguments]    ${title_base}    ${text}    ${summary}    ${screenshot}
    ${title}=    Unique Name    ${title_base}
    Create Wiki Page    ${title}
    Wait For Wiki Page Title    ${title}
    Wait For Wiki Page Link    ${title}
    Wait For Wiki Page Version    1
    Write Paragraph And Save    ${text}    ${summary}
    Wait For Wiki Page Version    2
    ${content}=    Get Wiki Page Text
    Should Contain    ${content}    ${text}
    Take Wiki Screenshot    ${screenshot}
    Open Wiki History
    ${count}=    Get Wiki Version Count
    Should Be Equal As Integers    ${count}    2

Wiki - Regimento Exige Secoes Obrigatorias
    [Documentation]    O regimento nasce em rascunho e vai para revisão; aprovar sem as seções obrigatórias é recusado,
    ...    com o aviso das seções que faltam; e volta para rascunho.
    [Arguments]    ${title_base}    ${screenshot}
    ${title}=    Unique Name    ${title_base}
    Create Wiki Regimento    ${title}
    Wait For Wiki Page Title    ${title}
    Wait For Wiki Status    Rascunho
    ${missing}=    Get Wiki Missing Text
    Should Contain    ${missing}    Identificação
    Click Submit For Review
    Wait For Wiki Status    Em revisão
    Click Approve
    Wait For Wiki Error
    ${error}=    Get Wiki Error Text
    Should Contain    ${error}    seções obrigatórias
    Take Wiki Screenshot    ${screenshot}
    Click Back To Draft
    Wait For Wiki Status    Rascunho

Wiki - Sem Aprovacao Para Quem Nao Aprova
    [Documentation]    O PM envia o regimento para revisão, mas não vê "Aprovar": o aprovador é o BUSINESS_OWNER.
    [Arguments]    ${title_base}
    ${title}=    Unique Name    ${title_base}
    Create Wiki Regimento    ${title}
    Wait For Wiki Status    Rascunho
    Click Submit For Review
    Wait For Wiki Status    Em revisão
    ${count}=    Get Approve Button Count
    Should Be Equal As Integers    ${count}    0
