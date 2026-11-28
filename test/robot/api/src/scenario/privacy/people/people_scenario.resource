*** Settings ***
Resource            ../../../flow/privacy/people/people_flow.resource

*** Keywords ***
PEOPLE - DIRECTORY AND EXPORT
    [Documentation]    Cenário de GET /api/v1/admin/people e GET /api/v1/admin/people/{id}/personal-data.
    [Arguments]    ${persona}    ${username_part}
    Pessoas - Diretorio E Exportacao    ${persona}    ${username_part}

PEOPLE - ANONYMIZE REFUSED
    [Documentation]    Cenário das recusas de POST /api/v1/admin/people/{id}/anonymize.
    [Arguments]    ${persona}    ${missing_id}
    Pessoas - Anonimizacao Recusada    ${persona}    ${missing_id}
