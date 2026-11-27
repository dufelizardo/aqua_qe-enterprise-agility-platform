*** Settings ***
Documentation       Estrutura organizacional (SSD 3.2) e a permissão "gerir configuração" (SSD 2 e 2.1): escolhe o
...                 caminho pelo status esperado e faz as asserções.
Library             String
Resource            ../../../pom/organization/org_units/org_units_pom.resource
Resource            ../../common/schema_validation_flow.resource
Resource            ../../../resource/config/schema/schema.resource

*** Keywords ***
Get Root Org Unit Id
    [Documentation]    Id da unidade raiz da instalação (SSD 8.5).
    ${params}=    Create Dictionary    level=ENTERPRISE
    ${response}=    GET Org Units As Persona    viewer    ${params}
    Should Be Equal As Integers    ${response.status_code}    200
    RETURN    ${response.json()}[items][0][id]

Create Org Unit As Admin
    [Documentation]    Cria uma unidade com nome único e devolve o id.
    [Arguments]    ${name}    ${level}    ${parent_id}
    ${suffix}=    Generate Random String    6    [LOWER][NUMBERS]
    ${payload}=    Create Dictionary    name=${name} ${suffix}    level=${level}    parentId=${parent_id}
    ${response}=    POST Org Unit As Persona    admin    ${payload}
    Should Be Equal As Integers    ${response.status_code}    201
    RETURN    ${response.json()}[id]

Validate Error Body
    [Arguments]    ${response}    ${status}    ${code}
    Should Be Equal As Integers    ${response.status_code}    ${status}
    Validate Object Schema    ${response.json()}    ${ORGANIZATION_ERROR_SCHEMA}
    Should Be Equal    ${response.json()}[code]    ${code}

GET Org Levels - Status Code 200
    [Documentation]    Os cinco níveis da instalação padrão, em ordem, com o Flight Level de cada um.
    [Arguments]    ${persona}
    ${response}=    GET Org Levels As Persona    ${persona}
    Should Be Equal As Integers    ${response.status_code}    200
    ${levels}=    Set Variable    ${response.json()}[items]
    Validate Array Schema    ${levels}    ${ORGANIZATION_LEVEL_SCHEMA}
    ${keys}=    Evaluate    [l['key'] for l in $levels]
    ${expected}=    Create List    ENTERPRISE    BUSINESS_UNIT    PORTFOLIO    CADEIA    SQUAD
    Lists Should Be Equal    ${keys}    ${expected}
    ${flight}=    Evaluate    {l['key']: l['flightLevel'] for l in $levels}
    Should Be Equal    ${flight}[PORTFOLIO]    FL3
    Should Be Equal    ${flight}[CADEIA]    FL2
    Should Be Equal    ${flight}[SQUAD]    FL1

POST Org Unit - Status Code 201
    [Documentation]    O administrador cria um Portfólio sob a raiz; o detalhe traz o caminho até a raiz.
    [Arguments]    ${persona}    ${name}
    ${root}=    Get Root Org Unit Id
    ${payload}=    Create Dictionary    name=${name}    level=PORTFOLIO    parentId=${root}
    ${response}=    POST Org Unit As Persona    ${persona}    ${payload}
    Should Be Equal As Integers    ${response.status_code}    201
    ${body}=    Set Variable    ${response.json()}
    Validate Object Schema    ${body}    ${ORGANIZATION_UNIT_DETAIL_SCHEMA}
    Should Be Equal    ${body}[name]    ${name}
    Should Be Equal    ${body}[parentId]    ${root}
    Should Be Equal    ${body}[path][0][id]    ${root}
    Should Be Equal As Integers    ${body}[version]    0

POST Org Unit - Status Code 403
    [Documentation]    Quem não tem o papel de gerir configuração recebe 403 (vê a raiz, mas não cria nela).
    [Arguments]    ${persona}    ${name}
    ${root}=    Get Root Org Unit Id
    ${payload}=    Create Dictionary    name=${name}    level=PORTFOLIO    parentId=${root}
    ${response}=    POST Org Unit As Persona    ${persona}    ${payload}
    Validate Error Body    ${response}    403    FORBIDDEN

POST Org Unit - Status Code 400 - Nivel Do Pai
    [Documentation]    O nível do pai precisa ter posição anterior: uma Cadeia não fica sob um Squad.
    [Arguments]    ${persona}    ${name}
    ${root}=    Get Root Org Unit Id
    ${squad}=    Create Org Unit As Admin    Robot Squad    SQUAD    ${root}
    ${payload}=    Create Dictionary    name=${name}    level=CADEIA    parentId=${squad}
    ${response}=    POST Org Unit As Persona    ${persona}    ${payload}
    Validate Error Body    ${response}    400    VALIDATION_FAILED
    Should Be Equal    ${response.json()}[errors][0][field]    level

GET Org Unit - Status Code 404
    [Documentation]    Unidade inexistente responde 404 NOT_FOUND.
    [Arguments]    ${persona}    ${unit_id}
    ${response}=    GET Org Unit As Persona    ${persona}    ${unit_id}
    Validate Error Body    ${response}    404    NOT_FOUND

PUT Org Unit Link - Status Code 204 - Cadeia Compartilhada
    [Documentation]    Cenário C2 (SSD 2.1): a Cadeia sob o Portfólio A passa a atender ao Portfólio B.
    [Arguments]    ${persona}    ${portfolio_name}    ${cadeia_name}
    ${root}=    Get Root Org Unit Id
    ${portfolio_a}=    Create Org Unit As Admin    ${portfolio_name} A    PORTFOLIO    ${root}
    ${portfolio_b}=    Create Org Unit As Admin    ${portfolio_name} B    PORTFOLIO    ${root}
    ${cadeia}=    Create Org Unit As Admin    ${cadeia_name}    CADEIA    ${portfolio_a}
    ${response}=    PUT Org Unit Link As Persona    ${persona}    ${cadeia}    ${portfolio_b}
    Should Be Equal As Integers    ${response.status_code}    204
    ${links}=    GET Org Unit Links As Persona    viewer    ${cadeia}
    ${serves}=    Evaluate    [u['id'] for u in $links.json()['serves']]
    Should Contain    ${serves}    ${portfolio_b}
    ${served}=    GET Org Unit Links As Persona    viewer    ${portfolio_b}
    ${served_by}=    Evaluate    [u['id'] for u in $served.json()['servedBy']]
    Should Contain    ${served_by}    ${cadeia}

PUT Org Unit Link - Status Code 400 - Mesma Linha
    [Documentation]    O vínculo só liga ramos diferentes: uma Cadeia não "atende" ao próprio Portfólio.
    [Arguments]    ${persona}    ${portfolio_name}    ${cadeia_name}
    ${root}=    Get Root Org Unit Id
    ${portfolio}=    Create Org Unit As Admin    ${portfolio_name}    PORTFOLIO    ${root}
    ${cadeia}=    Create Org Unit As Admin    ${cadeia_name}    CADEIA    ${portfolio}
    ${response}=    PUT Org Unit Link As Persona    ${persona}    ${cadeia}    ${portfolio}
    Validate Error Body    ${response}    400    VALIDATION_FAILED

PUT Org Unit Link - Status Code 403
    [Documentation]    Quem não gere configuração não cria vínculos.
    [Arguments]    ${persona}    ${portfolio_name}    ${cadeia_name}
    ${root}=    Get Root Org Unit Id
    ${portfolio_a}=    Create Org Unit As Admin    ${portfolio_name} A    PORTFOLIO    ${root}
    ${portfolio_b}=    Create Org Unit As Admin    ${portfolio_name} B    PORTFOLIO    ${root}
    ${response}=    PUT Org Unit Link As Persona    ${persona}    ${portfolio_a}    ${portfolio_b}
    Validate Error Body    ${response}    403    FORBIDDEN

GET Org Unit Deactivation Impact - Status Code 200
    [Documentation]    #35 (SSD 3.2): o aviso conta a Cadeia ativa abaixo e o vínculo; desativar continua permitido.
    [Arguments]    ${persona}    ${portfolio_name}    ${cadeia_name}
    ${root}=    Get Root Org Unit Id
    ${portfolio_a}=    Create Org Unit As Admin    ${portfolio_name} A    PORTFOLIO    ${root}
    ${portfolio_b}=    Create Org Unit As Admin    ${portfolio_name} B    PORTFOLIO    ${root}
    ${cadeia}=    Create Org Unit As Admin    ${cadeia_name}    CADEIA    ${portfolio_a}
    ${linked}=    PUT Org Unit Link As Persona    admin    ${portfolio_a}    ${portfolio_b}
    Should Be Equal As Integers    ${linked.status_code}    204
    ${response}=    GET Org Unit Deactivation Impact As Persona    ${persona}    ${portfolio_a}
    Should Be Equal As Integers    ${response.status_code}    200
    Should Be Equal As Integers    ${response.json()}[activeSubunits]    1
    Should Be Equal As Integers    ${response.json()}[openArtifacts]    0
    Should Be Equal As Integers    ${response.json()}[activeRoleAssignments]    0
    Should Be Equal As Integers    ${response.json()}[links]    1

GET Org Unit Deactivation Impact - Status Code 403
    [Documentation]    Só quem gere configuração (e pode desativar) vê o aviso.
    [Arguments]    ${persona}    ${portfolio_name}    ${cadeia_name}
    ${root}=    Get Root Org Unit Id
    ${portfolio}=    Create Org Unit As Admin    ${portfolio_name}    PORTFOLIO    ${root}
    ${response}=    GET Org Unit Deactivation Impact As Persona    ${persona}    ${portfolio}
    Validate Error Body    ${response}    403    FORBIDDEN

Organization - Get Deactivation Impact - GET - By HTTP Status Code
    [Documentation]    Executa o cenário de GET /org-units/{id}/deactivation-impact correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${persona}    ${portfolio_name}    ${cadeia_name}
    IF    '${meuHTTP}' == '200'
        GET Org Unit Deactivation Impact - Status Code 200    ${persona}    ${portfolio_name}    ${cadeia_name}
    ELSE IF    '${meuHTTP}' == '403'
        GET Org Unit Deactivation Impact - Status Code 403    ${persona}    ${portfolio_name}    ${cadeia_name}
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para GET /org-units/{id}/deactivation-impact.
    END

Organization - Post Org Unit - POST - By HTTP Status Code
    [Documentation]    Executa o cenário de POST /org-units correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${persona}    ${name}
    ${suffix}=    Generate Random String    6    [LOWER][NUMBERS]
    IF    '${meuHTTP}' == '201'
        POST Org Unit - Status Code 201    ${persona}    ${name} ${suffix}
    ELSE IF    '${meuHTTP}' == '403'
        POST Org Unit - Status Code 403    ${persona}    ${name} ${suffix}
    ELSE IF    '${meuHTTP}' == '400'
        POST Org Unit - Status Code 400 - Nivel Do Pai    ${persona}    ${name} ${suffix}
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para POST /org-units.
    END

Organization - Put Org Unit Link - PUT - By HTTP Status Code
    [Documentation]    Executa o cenário de PUT /org-units/{id}/links/{targetId} correspondente ao status esperado.
    [Arguments]    ${meuHTTP}    ${persona}    ${portfolio_name}    ${cadeia_name}
    IF    '${meuHTTP}' == '204'
        PUT Org Unit Link - Status Code 204 - Cadeia Compartilhada    ${persona}    ${portfolio_name}    ${cadeia_name}
    ELSE IF    '${meuHTTP}' == '400'
        PUT Org Unit Link - Status Code 400 - Mesma Linha    ${persona}    ${portfolio_name}    ${cadeia_name}
    ELSE IF    '${meuHTTP}' == '403'
        PUT Org Unit Link - Status Code 403    ${persona}    ${portfolio_name}    ${cadeia_name}
    ELSE
        Fail    Status HTTP '${meuHTTP}' não previsto para PUT /org-units/{id}/links/{targetId}.
    END
