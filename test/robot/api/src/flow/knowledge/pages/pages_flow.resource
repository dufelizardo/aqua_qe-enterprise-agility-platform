*** Settings ***
Documentation       Páginas de conhecimento (SSD 3.7.1; Proposta 07, Fase A): escolhe o caminho pelo status esperado e
...                 faz as asserções. As personas têm papel na raiz, então leem tudo pelas regras 1 a 7; a leitura só do
...                 publicado (regra 9) é coberta no JUnit KnowledgeIntegrationTests.
Library             Collections
Resource            ../../../pom/knowledge/pages/pages_pom.resource
Resource            ../../../pom/identity/get_me/get_me_pom.resource
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
    [Arguments]    ${persona}    ${unit}    ${title}    ${parent}=${None}
    ${payload}=    Create Dictionary    title=${title}    parentId=${parent}
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
    ${payload}=    Create Dictionary    title=${title}
    ${created}=    POST Regimento As Persona    pm    ${unit}    ${payload}
    Should Be Equal As Integers    ${created.status_code}    201
    ${reg}=    Set Variable    ${created.json()}
    Should Be Equal    ${reg}[status]    DRAFT
    ${again}=    POST Regimento As Persona    pm    ${unit}    ${payload}
    Validate Error Body    ${again}    409    REGIMENTO_EXISTS
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

Pages - Reorganizar
    [Documentation]    PROP-002: B passa para antes de A; depois A vai para dentro de B, sem versão nova; mover A
    ...    para dentro de si mesma é recusado.
    [Arguments]    ${unit_name}    ${title}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${a}=    Create Page    pm    ${unit}    ${title} A
    ${b}=    Create Page    pm    ${unit}    ${title} B
    ${payload}=    Create Dictionary    beforeId=${a}[id]
    ${response}=    POST Page Rank As Persona    pm    ${b}[id]    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
    ${listing}=    GET Pages As Persona    pm    ${unit}
    Should Be Equal    ${listing.json()}[items][0][id]    ${b}[id]
    Should Be Equal    ${listing.json()}[items][1][id]    ${a}[id]
    ${payload}=    Create Dictionary    parentId=${b}[id]
    ${response}=    POST Page Rank As Persona    pm    ${a}[id]    ${payload}
    Should Be Equal As Integers    ${response.status_code}    200
    Should Be Equal    ${response.json()}[parentId]    ${b}[id]
    Should Be Equal As Integers    ${response.json()}[versionNumber]    1
    ${payload}=    Create Dictionary    parentId=${a}[id]
    ${response}=    POST Page Rank As Persona    pm    ${a}[id]    ${payload}
    Validate Error Body    ${response}    400    VALIDATION_FAILED

Pages - Fazer Uma Copia
    [Documentation]    Menu ⋯: a cópia nasce logo depois da original, com "Cópia de …" e o conteúdo atual; quem só lê
    ...    não copia.
    [Arguments]    ${unit_name}    ${title}    ${text}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${original}=    Create Page    pm    ${unit}    ${title}
    ${last}=    Create Page    pm    ${unit}    ${title} fim
    ${saved}=    Save Page    pm    ${original}    1    ${text}
    Should Be Equal As Integers    ${saved.status_code}    200
    ${empty}=    Create Dictionary
    ${response}=    POST Page Copy As Persona    pm    ${original}[id]    ${empty}
    Should Be Equal As Integers    ${response.status_code}    201
    Should Be Equal    ${response.json()}[title]    Cópia de ${title}
    Should Be Equal    ${response.json()}[sections][0][blocks][0][text]    ${text}
    ${listing}=    GET Pages As Persona    pm    ${unit}
    ${order}=    Evaluate    [p['id'] for p in $listing.json()['items']]
    ${expected}=    Create List    ${original}[id]    ${response.json()}[id]    ${last}[id]
    Lists Should Be Equal    ${order}    ${expected}
    ${denied}=    POST Page Copy As Persona    viewer    ${original}[id]    ${empty}
    Should Be Equal As Integers    ${denied.status_code}    403

Pages - Comentar Mencionar E Favoritar
    [Documentation]    #85: o PM comenta citando um trecho e menciona o leitor (achado pela busca de pessoas); o leitor
    ...    recebe a notificação de menção; marcar a página com estrela a põe nos favoritos.
    [Arguments]    ${unit_name}    ${title}    ${comment_text}    ${quote}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${page}=    Create Page    pm    ${unit}    ${title}
    ${me}=    GET Me As Persona    viewer
    ${viewer_id}=    Set Variable    ${me.json()}[id]
    ${viewer_name}=    Set Variable    ${me.json()}[name]
    ${people}=    GET Page People As Persona    pm    ${page}[id]    ${viewer_name}
    Should Be Equal As Integers    ${people.status_code}    200
    ${ids}=    Evaluate    [p['id'] for p in $people.json()['items']]
    List Should Contain Value    ${ids}    ${viewer_id}
    ${mentions}=    Create List    ${viewer_id}
    ${payload}=    Create Dictionary    body=@${viewer_name} ${comment_text}    quote=${quote}    mentionIds=${mentions}
    ${created}=    POST Page Comment As Persona    pm    ${page}[id]    ${payload}
    Should Be Equal As Integers    ${created.status_code}    201
    Should Be Equal    ${created.json()}[quote]    ${quote}
    Should Be Equal    ${created.json()}[mentions][0][id]    ${viewer_id}
    ${listing}=    GET Page Comments As Persona    viewer    ${page}[id]
    Should Be Equal As Integers    ${listing.status_code}    200
    Should Be Equal    ${listing.json()}[items][0][id]    ${created.json()}[id]
    Wait Until Keyword Succeeds    30s    1s    Notification Should Include    viewer    ${page}[id]    MENTION
    ${empty}=    Create Dictionary    body=${SPACE}
    ${invalid}=    POST Page Comment As Persona    pm    ${page}[id]    ${empty}
    Validate Error Body    ${invalid}    400    VALIDATION_FAILED
    ${star}=    PUT Favorite Page As Persona    viewer    ${page}[id]
    Should Be Equal As Integers    ${star.status_code}    204
    ${favorites}=    GET Favorites As Persona    viewer
    ${pages}=    Evaluate    [f['pageId'] for f in $favorites.json()['items']]
    List Should Contain Value    ${pages}    ${page}[id]

Notification Should Include
    [Documentation]    A persona tem um aviso do tipo informado sobre a página (a outbox entrega em segundos).
    [Arguments]    ${persona}    ${page_id}    ${kind}
    ${inbox}=    GET Notifications As Persona    ${persona}
    Should Be Equal As Integers    ${inbox.status_code}    200
    ${matches}=    Evaluate    [n for n in $inbox.json()['items'] if n['targetId'] == $page_id and n['kind'] == $kind]
    Should Not Be Empty    ${matches}

Pages - Icone Tarefas E Cores
    [Documentation]    #86: o ícone (emoji) muda sem criar versão; a lista de tarefas e as cores #rrggbb são gravadas;
    ...    uma cor fora do formato é recusada.
    [Arguments]    ${unit_name}    ${title}    ${icon}    ${color}
    ${unit}=    Create Knowledge Unit    ${unit_name}
    ${page}=    Create Page    pm    ${unit}    ${title}
    ${payload}=    Create Dictionary    icon=${icon}
    ${patched}=    PATCH Page As Persona    pm    ${page}[id]    ${payload}
    Should Be Equal As Integers    ${patched.status_code}    200
    Should Be Equal    ${patched.json()}[icon]    ${icon}
    Should Be Equal As Integers    ${patched.json()}[versionNumber]    1
    ${invalid}=    Create Dictionary    icon=abc
    ${refused}=    PATCH Page As Persona    pm    ${page}[id]    ${invalid}
    Validate Error Body    ${refused}    400    VALIDATION_FAILED
    ${saved}=    PUT Page As Persona    pm    ${page}[id]    ${{ {'baseVersion': 1, 'sections': [{'key': 'content', 'blocks': [{'type': 'richtext', 'doc': {'type': 'doc', 'content': [{'type': 'taskList', 'content': [{'type': 'taskItem', 'attrs': {'checked': False}, 'content': [{'type': 'paragraph', 'content': [{'type': 'text', 'text': 'Revisar', 'marks': [{'type': 'textStyle', 'attrs': {'color': $color}}]}]}]}]}]}}]}]} }}
    Should Be Equal As Integers    ${saved.status_code}    200
    Should Be Equal    ${saved.json()}[sections][0][blocks][0][doc][content][0][type]    taskList
    ${bad}=    PUT Page As Persona    pm    ${page}[id]    ${{ {'baseVersion': 2, 'sections': [{'key': 'content', 'blocks': [{'type': 'richtext', 'doc': {'type': 'doc', 'content': [{'type': 'paragraph', 'content': [{'type': 'text', 'text': 'x', 'marks': [{'type': 'highlight', 'attrs': {'color': 'red;background:url(x)'}}]}]}]}}]}]} }}
    Validate Error Body    ${bad}    400    VALIDATION_FAILED
