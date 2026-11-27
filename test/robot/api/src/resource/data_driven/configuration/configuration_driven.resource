*** Comments ***
    Dados do módulo configuration: campos customizados por tipo de artefato (#17; REV-12). A chave recebe um sufixo
    aleatório no FLOW.

*** Variables ***
# custom_fields (POST /api/v1/artifact-types/{key}/custom-fields, PATCH e DELETE /api/v1/custom-fields/{id})
${custom_field_type}              INITIATIVE
${custom_field_key_base}          robotCanal
${custom_field_label}             Canal Robot
@{custom_field_options}           App    Web
${custom_field_valid_option}      Web
${custom_field_invalid_option}    Loja
${custom_field_title}             Robot Iniciativa Campos
${custom_field_gate_title}        Robot Iniciativa Gate Campo
