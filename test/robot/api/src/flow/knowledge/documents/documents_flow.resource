*** Settings ***
Documentation       Documentos da Iniciativa e da Feature (SSD 3.7; #63): cria a Iniciativa com o fluxo de artefatos e
...                 faz as asserções do ciclo PENDING → IN_PROGRESS → APPROVED → IN_PROGRESS.
Library             Collections
Resource            ../../../pom/knowledge/documents/documents_pom.resource
Resource            ../../workitem/artifacts/artifacts_flow.resource

*** Keywords ***
Create Initiative For Documents
    [Documentation]    Portfólio novo e uma Iniciativa nele, criada pelo PM; devolve a chave.
    [Arguments]    ${title}
    ${portfolio}    ${cadeia}=    Create Portfolio And Cadeia
    ${init}=    Create Artifact    pm    INITIATIVE    ${title}    ${portfolio}
    RETURN    ${init}[key]

PRD Sections
    [Documentation]    Todas as seções do PRD; as da lista com um parágrafo, as demais vazias.
    [Arguments]    ${artifact}    @{filled}
    ${doc}=    GET Artifact Document As Persona    pm    ${artifact}    PRD
    ${sections}=    Create List
    FOR    ${section}    IN    @{doc.json()}[sections]
        ${blocks}=    Create List
        IF    $section['key'] in $filled
            ${block}=    Create Dictionary    type=paragraph    text=Texto de ${section}[key]
            Append To List    ${blocks}    ${block}
        END
        ${item}=    Create Dictionary    key=${section}[key]    blocks=${blocks}
        Append To List    ${sections}    ${item}
    END
    RETURN    ${sections}

Save PRD
    [Arguments]    ${persona}    ${artifact}    ${base}    @{filled}
    ${sections}=    PRD Sections    ${artifact}    @{filled}
    ${base}=    Convert To Integer    ${base}
    ${payload}=    Create Dictionary    baseVersion=${base}    sections=${sections}
    ${response}=    PUT Artifact Document As Persona    ${persona}    ${artifact}    PRD    ${payload}
    RETURN    ${response}

Change PRD Status
    [Arguments]    ${persona}    ${artifact}    ${status}    ${comment}=${None}
    ${payload}=    Create Dictionary    status=${status}
    IF    $comment is not None
        Set To Dictionary    ${payload}    comment=${comment}
    END
    ${response}=    POST Artifact Document Status As Persona    ${persona}    ${artifact}    PRD    ${payload}
    RETURN    ${response}

Documents - Ciclo Do PRD
    [Documentation]    Os cinco nascem PENDING; salvar passa a IN_PROGRESS; aprovar exige as obrigatórias; aprovado não
    ...    se edita; reabrir exige comentário e volta a IN_PROGRESS.
    [Arguments]    ${title}
    ${init}=    Create Initiative For Documents    ${title}
    ${listing}=    GET Artifact Documents As Persona    pm    ${init}
    Should Be Equal As Integers    ${listing.status_code}    200
    ${types}=    Evaluate    [d['type'] for d in $listing.json()['items']]
    ${expected}=    Create List    PRD    SAD    SSD    UX    QA
    Lists Should Be Equal    ${types}    ${expected}
    FOR    ${doc}    IN    @{listing.json()}[items]
        Should Be Equal    ${doc}[status]    PENDING
    END
    ${saved}=    Save PRD    pm    ${init}    0    context
    Should Be Equal As Integers    ${saved.status_code}    200
    Should Be Equal    ${saved.json()}[status]    IN_PROGRESS
    ${conflict}=    Save PRD    pm    ${init}    0    context
    Validate Error Body    ${conflict}    409    VERSION_CONFLICT
    ${incomplete}=    Change PRD Status    pm    ${init}    APPROVED
    Validate Error Body    ${incomplete}    422    DOCUMENT_INCOMPLETE
    ${saved}=    Save PRD    pm    ${init}    1    context    objectives    scope    requirements
    Should Be Equal As Integers    ${saved.json()}[versionNumber]    2
    ${approved}=    Change PRD Status    pm    ${init}    APPROVED
    Should Be Equal As Integers    ${approved.status_code}    200
    Should Be Equal    ${approved.json()}[status]    APPROVED
    Should Be Equal As Integers    ${approved.json()}[approvedVersion]    2
    ${locked}=    Save PRD    pm    ${init}    2    context
    Validate Error Body    ${locked}    409    DOCUMENT_NOT_EDITABLE
    ${reopened}=    Change PRD Status    pm    ${init}    IN_PROGRESS    comment=Faltou o escopo de dados
    Should Be Equal As Integers    ${reopened.status_code}    200
    Should Be Equal    ${reopened.json()}[status]    IN_PROGRESS
    ${versions}=    GET Artifact Document Versions As Persona    pm    ${init}    PRD
    Length Should Be    ${versions.json()}    2

Documents - Leitor Nao Edita
    [Documentation]    Quem só lê vê os documentos, mas não grava.
    [Arguments]    ${title}
    ${init}=    Create Initiative For Documents    ${title}
    ${listing}=    GET Artifact Documents As Persona    viewer    ${init}
    Should Be Equal As Integers    ${listing.status_code}    200
    Should Be Equal    ${listing.json()}[items][0][canEdit]    ${False}
    ${denied}=    Save PRD    viewer    ${init}    0    context
    Validate Error Body    ${denied}    403    FORBIDDEN
