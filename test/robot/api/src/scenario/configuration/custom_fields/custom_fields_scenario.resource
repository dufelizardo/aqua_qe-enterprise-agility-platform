*** Settings ***
Resource            ../../../flow/configuration/custom_fields/custom_fields_flow.resource

*** Keywords ***
CONFIGURATION - CUSTOM FIELD DECLARE WRITE REMOVE
    [Documentation]    Cenário do catálogo de campos e da gravação só dos declarados (#17).
    [Arguments]    ${type}    ${key_base}    ${label}    ${options}    ${valid_option}    ${invalid_option}    ${title}
    Custom Fields - Declarar Gravar E Remover    ${type}    ${key_base}    ${label}    ${options}    ${valid_option}
    ...    ${invalid_option}    ${title}

CONFIGURATION - CUSTOM FIELD UNDECLARED IN GATE
    [Documentation]    Cenário do gate que cita campo não declarado (#17; REV-12).
    [Arguments]    ${type}    ${key_base}    ${title}
    Custom Fields - Gate Com Campo Nao Declarado    ${type}    ${key_base}    ${title}
