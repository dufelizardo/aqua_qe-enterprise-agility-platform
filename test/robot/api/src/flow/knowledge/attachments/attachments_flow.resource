*** Settings ***
Documentation       Anexos dos artefatos (SSD 3.7; #13, REV-08): cria a Iniciativa com o fluxo de artefatos e faz as
...                 asserções de envio, listagem, download e remoção.
Resource            ../../../pom/knowledge/attachments/attachments_pom.resource
Resource            ../../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Attachments - Enviar Listar Baixar E Remover
    [Documentation]    O PM envia; o leitor lista e baixa (como arquivo, com nosniff), mas não remove; o PM remove e o
    ...    anexo some da lista e do download.
    [Arguments]    ${title}    ${file_name}    ${content}    ${content_type}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${title}    ${portfolio}
    ${sent}=    POST Attachment As Persona    pm    ${init}[key]    ${file_name}    ${content}    ${content_type}
    Should Be Equal As Integers    ${sent.status_code}    201
    Should Be Equal    ${sent.json()}[fileName]    ${file_name}
    Should Be Equal    ${sent.json()}[contentType]    ${content_type}
    ${id}=    Set Variable    ${sent.json()}[id]
    ${list}=    GET Attachments As Persona    viewer    ${init}[key]
    Should Be Equal As Integers    ${list.status_code}    200
    Should Not Be True    ${list.json()}[canEdit]
    Should Be Equal    ${list.json()}[items][0][id]    ${id}
    ${download}=    GET Attachment Content As Persona    viewer    ${id}
    Should Be Equal As Integers    ${download.status_code}    200
    ${text}=    Evaluate    $download.content.decode("utf-8")
    Should Be Equal    ${text}    ${content}
    Should Contain    ${download.headers}[Content-Disposition]    attachment
    Should Be Equal    ${download.headers}[X-Content-Type-Options]    nosniff
    ${denied}=    DELETE Attachment As Persona    viewer    ${id}
    Should Be Equal As Integers    ${denied.status_code}    403
    ${removed}=    DELETE Attachment As Persona    pm    ${id}
    Should Be Equal As Integers    ${removed.status_code}    204
    ${after}=    GET Attachments As Persona    pm    ${init}[key]
    Should Be Empty    ${after.json()}[items]
    ${gone}=    GET Attachment Content As Persona    pm    ${id}
    Should Be Equal As Integers    ${gone.status_code}    404

Attachments - Leitor Nao Envia E Vazio Recusado
    [Documentation]    Quem só lê não envia (403); arquivo vazio dá 400 no campo file.
    [Arguments]    ${title}    ${file_name}    ${content_type}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${title}    ${portfolio}
    ${reader}=    POST Attachment As Persona    viewer    ${init}[key]    ${file_name}    x    ${content_type}
    Should Be Equal As Integers    ${reader.status_code}    403
    ${empty}=    POST Attachment As Persona    pm    ${init}[key]    ${file_name}    ${EMPTY}    ${content_type}
    Validate Error Body    ${empty}    400    VALIDATION_FAILED
    Should Be Equal    ${empty.json()}[errors][0][field]    file
