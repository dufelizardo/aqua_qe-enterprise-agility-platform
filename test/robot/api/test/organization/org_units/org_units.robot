*** Settings ***
Documentation       Suíte da estrutura organizacional (SSD 3.2) e da permissão "gerir configuração" (SSD 2 e 2.1).
Resource            ../../../src/scenario/organization/org_units/org_units_scenario.resource
Resource            ../../../src/resource/config/driven/data_driven.resource
Metadata            Suite    Organization
Metadata            Owner    AQuA-QE
Metadata            Version    1.0
Metadata            Created On    2026-11-20
Metadata            Project    Layered Keyword Driven Framework (LKDF)

*** Test Cases ***
CT-001 - ADR-14: Validate GET Org Levels - HTTP 200 OK
    [Documentation]    Os cinco níveis da instalação padrão, com o Flight Level de cada um.
    [Tags]    GET    GetOrgLevels    HTTP200    ADR-14    smoke
    ORGANIZATION - GET ORG LEVELS - GET    viewer

CT-002 - ADR-14: Validate POST Org Unit - HTTP 201 CREATED
    [Documentation]    O administrador cria um Portfólio sob a raiz.
    [Tags]    POST    PostOrgUnit    HTTP201    ADR-14
    ORGANIZATION - POST ORG UNIT - POST    201    admin    ${post_org_unit_portfolio_name}

CT-003 - REV-01: Validate POST Org Unit - HTTP 403 FORBIDDEN (sem gerir configuração)
    [Documentation]    Nenhum papel de negócio cria unidades.
    [Tags]    POST    PostOrgUnit    HTTP403    REV-01    security    ASVS-V4
    [Template]    ORGANIZATION - POST ORG UNIT - POST
    403    pm    ${post_org_unit_portfolio_name}
    403    bo    ${post_org_unit_portfolio_name}
    403    viewer    ${post_org_unit_portfolio_name}

CT-004 - ADR-14: Validate POST Org Unit - HTTP 400 BAD REQUEST (nível do pai)
    [Documentation]    O nível do pai precisa ter posição anterior à do nível da unidade.
    [Tags]    POST    PostOrgUnit    HTTP400    ADR-14
    ORGANIZATION - POST ORG UNIT - POST    400    admin    ${post_org_unit_cadeia_name}

CT-005 - ADR-14: Validate GET Org Unit - HTTP 404 NOT FOUND
    [Documentation]    Unidade inexistente responde 404.
    [Tags]    GET    GetOrgUnit    HTTP404    ADR-14
    ORGANIZATION - GET ORG UNIT - GET - Inexistente    viewer    ${post_org_unit_unknown_id}

CT-006 - REV-04: Validate PUT Org Unit Link - HTTP 204 NO CONTENT (Cadeia compartilhada, cenário C2)
    [Documentation]    A Cadeia sob o Portfólio A passa a atender ao Portfólio B; o vínculo aparece nos dois lados.
    [Tags]    PUT    PutOrgUnitLink    HTTP204    REV-04
    ORGANIZATION - PUT ORG UNIT LINK - PUT    204    admin    ${post_org_unit_portfolio_name}
    ...    ${post_org_unit_cadeia_name}

CT-007 - REV-04: Validate PUT Org Unit Link - HTTP 400 BAD REQUEST (mesma linha da árvore)
    [Documentation]    O vínculo só liga ramos diferentes.
    [Tags]    PUT    PutOrgUnitLink    HTTP400    REV-04
    ORGANIZATION - PUT ORG UNIT LINK - PUT    400    admin    ${post_org_unit_portfolio_name}
    ...    ${post_org_unit_cadeia_name}

CT-008 - REV-01: Validate PUT Org Unit Link - HTTP 403 FORBIDDEN (sem gerir configuração)
    [Documentation]    Nenhum papel de negócio cria vínculos (SSD 2.1).
    [Tags]    PUT    PutOrgUnitLink    HTTP403    REV-01    security    ASVS-V4
    ORGANIZATION - PUT ORG UNIT LINK - PUT    403    tl    ${post_org_unit_portfolio_name}
    ...    ${post_org_unit_cadeia_name}
