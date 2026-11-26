*** Settings ***
Resource            ../../flow/inicio/inicio_flow.resource

*** Keywords ***
HOME - RECENT
    [Documentation]    Cenário de POST e GET /api/v1/me/recent ("Continuar de onde parou").
    [Arguments]    ${unit_name}    ${page_title}    ${artifact_title}
    Home - Recentes    ${unit_name}    ${page_title}    ${artifact_title}

HOME - NEWS
    [Documentation]    Cenário de GET /api/v1/me/news ("Novidades").
    [Arguments]    ${unit_name}    ${artifact_title}
    Home - Novidades    ${unit_name}    ${artifact_title}
