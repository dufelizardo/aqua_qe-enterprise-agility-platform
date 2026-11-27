*** Comments ***
    Dados do módulo board. Os nomes de template recebem sufixo aleatório no FLOW.

*** Variables ***
# board_templates (GET /api/v1/board-templates, POST /api/v1/boards/{id}/template; #50)
${board_template_builtin}     Kanban — Sustentação
@{board_template_columns}     Triagem    Em atendimento    Validando    Resolvido
${board_template_name}        Robot Template
