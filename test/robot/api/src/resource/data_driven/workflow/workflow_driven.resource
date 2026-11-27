*** Comments ***
    Dados do módulo workflow. A Iniciativa de cada caso é criada no FLOW, com nome próprio.

*** Variables ***
# post_transition (POST /api/v1/artifacts/{artifact}/transitions)
${post_transition_title}        Robot Iniciativa Transição
${post_transition_comment}      Pronta para o backlog, pela suíte Robot.

# workflow_designer (POST /api/v1/org-units/{id}/workflows, POST /api/v1/workflows/{id}/versions e /migration; #49)
${workflow_designer_type}          SUSTAINING_FEATURE
${workflow_designer_title}         Robot Sustentação Workflow
${workflow_designer_status_key}    ROBOT_TRIAGE
${workflow_designer_status_name}   Em Triagem Robot
