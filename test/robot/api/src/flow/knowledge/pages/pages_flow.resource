*** Settings ***
Documentation       Páginas de conhecimento (SSD 3.7.1; Proposta 07, Fase A): escolhe o caminho pelo status esperado e
...                 faz as asserções. As personas têm papel na raiz, então leem tudo pelas regras 1 a 7; a leitura só do
...                 publicado (regra 9) é coberta no JUnit KnowledgeIntegrationTests.
Library             Collections
Resource            ../../../pom/knowledge/pages/pages_pom.resource
Resource            ../../organization/org_units/org_units_flow.resource

*** Keywords ***
Create Knowledge Unit
    [Documentation]    Portfólio novo sob a raiz, para a suíte rodar de novo no mesmo banco.
    [Arguments]    ${name}
    ${root}=    Get Root Org Unit Id
    ${unit}=    Create Org Unit As Admin    ${name}    PORTFOLIO    ${root}
    RETURN    ${unit}

Create Page
    [Documentation]    Cria a página e exige 201; devolve o corpo.
    [Arguments]    ${persona}    ${unit}    ${title}    ${type}=PAGE    ${parent}=${None}
    ${payload}=    Create Dictionary    title=${title}    type=${type}    parentId=${parent}
    ${response}=    POST Page As Persona    ${persona}    ${unit}    ${payload}
    Should Be Equal As Integers    ${response.status_code}    201
    RETURN    ${response.json()}

Sections With Text
    [Documentation]    Todas as seções da página com um parágrafo com o texto.
    [Arguments]    ${page}    ${text}
    ${sections}=    Create List
    FOR    ${section}    IN    @{page}[sections]
        ${block}=    Create Dictionary    type=paragraph    text=${text}
        ${blocks}=    Create List    ${block}
        ${item}=    Create Dictionary    key=${section}[key]    blocks=${blocks}
        Append To List    ${sections}    ${item}
    END
    RETURN    ${sections}

Save Page
    [Arguments]    ${persona}    ${page}    ${base}    ${text}
    ${sections}=    Sections With Text    ${page}    ${text}
    ${base}=    Convert To Integer    ${base}
    ${payload}=    Create Dictionary    baseVersion=${base}    sections=${sections}
    ${response}=    PUT Page As Persona    ${persona}    ${page}[id]    ${payload}
    RETURN    ${response}

Pages - Status Code 201
    [Documentation]    Página livre publica ao salvar; a subpágina fica sob ela; salvar sobre versão antiga dá conflito.
    [Arguments]    ${unit_name}    ${title}    ${text}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${page}=    Create Page    pm    ${unit}    ${title}
    Should Be Equal    ${page}[status]    PUBLISHED
    ${sub}=    Create Page    pm    ${unit}    ${title} - detalhe    parent=${page}[id]
    Should Be Equal    ${sub}[parentId]    ${page}[id]
    ${saved}=    Save Page    pm    ${page}    1    ${text}
    Should Be Equal As Integers    ${saved.status_code}    200
    Should Be Equal As Integers    ${saved.json()}[versionNumber]    2
    Should Be Equal    ${saved.json()}[sections][0][blocks][0][text]    ${text}
    ${conflict}=    Save Page    pm    ${page}    1    ${text} de novo
    Validate Error Body    ${conflict}    409    VERSION_CONFLICT
    ${versions}=    GET Page Versions As Persona    pm    ${page}[id]
    Length Should Be    ${versions.json()}[items]    2
    ${listing}=    GET Pages As Persona    viewer    ${unit}
    Should Be Equal As Integers    ${listing.status_code}    200
    Should Be Equal    ${listing.json()}[canEdit]    ${False}
    Length Should Be    ${listing.json()}[items]    2

Pages - Regimento Lifecycle
    [Documentation]    Regimento: rascunho, revisão, aprovação pelo BUSINESS_OWNER (o PM recebe 403), seções obrigatórias
    ...    exigidas, e publicação separada da aprovação.
    [Arguments]    ${unit_name}    ${title}    ${text}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${reg}=    Create Page    pm    ${unit}    ${title}    type=REGIMENTO
    Should Be Equal    ${reg}[status]    DRAFT
    ${response}=    POST Page Status As Persona    pm    ${reg}[id]    IN_REVIEW
    Should Be Equal As Integers    ${response.status_code}    200
    ${response}=    POST Page Status As Persona    pm    ${reg}[id]    APPROVED
    Validate Error Body    ${response}    403    FORBIDDEN
    ${response}=    POST Page Status As Persona    bo    ${reg}[id]    APPROVED
    Validate Error Body    ${response}    422    DOCUMENT_INCOMPLETE
    ${response}=    POST Page Status As Persona    bo    ${reg}[id]    DRAFT
    Should Be Equal As Integers    ${response.status_code}    200
    ${saved}=    Save Page    pm    ${reg}    1    ${text}
    Should Be Equal As Integers    ${saved.status_code}    200
    ${response}=    POST Page Status As Persona    pm    ${reg}[id]    IN_REVIEW
    Should Be Equal As Integers    ${response.status_code}    200
    ${response}=    POST Page Status As Persona    bo    ${reg}[id]    APPROVED
    Should Be Equal    ${response.json()}[status]    APPROVED
    Should Be Equal    ${response.json()}[publishedVersion]    ${None}
    ${response}=    POST Page Status As Persona    bo    ${reg}[id]    PUBLISHED
    Should Be Equal    ${response.json()}[status]    PUBLISHED
    Should Be Equal As Integers    ${response.json()}[publishedVersion]    2

Pages - Status Code 403
    [Documentation]    Quem só lê (VIEWER) não cria página (Proposta 07, EDIT_KNOWLEDGE).
    [Arguments]    ${unit_name}    ${title}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${payload}=    Create Dictionary    title=${title}
    ${response}=    POST Page As Persona    viewer    ${unit}    ${payload}
    Validate Error Body    ${response}    403    FORBIDDEN

Knowledge - Pages - By HTTP Status Code
    [Documentation]    Executa o cenário de páginas de conhecimento correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${unit_name}    ${title}    ${text}
    IF    '${meuHTTP}' == '201'
        Pages - Status Code 201    ${unit_name}    ${title}    ${text}
    ELSE IF    '${meuHTTP}' == '200'
        Pages - Regimento Lifecycle    ${unit_name}    ${title}    ${text}
    ELSE IF    '${meuHTTP}' == '403'
        Pages - Status Code 403    ${unit_name}    ${title}
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para as páginas de conhecimento.
    END
