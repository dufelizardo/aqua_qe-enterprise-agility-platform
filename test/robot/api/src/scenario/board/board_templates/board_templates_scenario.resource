*** Settings ***
Resource            ../../../flow/board/board_templates/board_templates_flow.resource

*** Keywords ***
BOARD - TEMPLATE APPLIED BY COPY AND SAVED FROM BOARD
    [Documentation]    Cenário do template aplicado por cópia e salvo a partir do quadro (#50).
    [Arguments]    ${builtin_name}    ${expected_columns}    ${template_base}
    Board Templates - Criar Quadro Pelo Template E Salvar Como Template    ${builtin_name}    ${expected_columns}
    ...    ${template_base}

BOARD - TEMPLATE OF ANOTHER LEVEL
    [Documentation]    Cenário do template de outro nível (#50).
    [Arguments]    ${builtin_name}
    Board Templates - Template De Outro Nivel    ${builtin_name}
