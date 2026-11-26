*** Settings ***
Resource            ../../../flow/workitem/default_owner/default_owner_flow.resource

*** Keywords ***
WORKITEM - DEFAULT OWNER FROM CREATOR
    [Documentation]    Cenário do responsável padrão "quem criou" (#43).
    [Arguments]    ${title}
    Default Owner - Quem Criou Fica Como Owner    ${title}

WORKITEM - DEFAULT OWNER INVALID RULE
    [Documentation]    Cenário da regra inválida (#43).
    Default Owner - Regra Invalida
