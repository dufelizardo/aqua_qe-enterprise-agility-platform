*** Settings ***
Resource            ../../../flow/configuration/workflows/workflows_flow.resource

*** Keywords ***
CONFIGURATION - WORKFLOW CUSTOMIZE PUBLISH MIGRATE
    [Documentation]    Cenário do workflow próprio da unidade, com nova versão e migração explícita (#49).
    [Arguments]    ${type}    ${title}    ${status_key}    ${status_name}
    Workflows - Personalizar Publicar E Migrar    ${type}    ${title}    ${status_key}    ${status_name}

CONFIGURATION - WORKFLOW INVALID VERSION
    [Documentation]    Cenário da versão inválida (#49).
    [Arguments]    ${type}
    Workflows - Versao Invalida    ${type}
