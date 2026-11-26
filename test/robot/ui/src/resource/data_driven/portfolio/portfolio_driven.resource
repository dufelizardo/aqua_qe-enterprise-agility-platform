*** Comments ***
    Dados da suíte de Portfolio. robot.admin tem PLATFORM_ADMIN na raiz, que é o único workspace dele: a seleção de
    workspace é pulada. Nomes recebem sufixo aleatório no FLOW.

*** Variables ***
${portfolio_admin_username}         robot.admin
${portfolio_admin_display_name}     Alice Admin
${portfolio_root_name}              Empresa
${portfolio_unit_base}              Robot UI Portfólio PF
${portfolio_unit_label}             Portfólio
${portfolio_initiative_base}        Robot UI Iniciativa
${portfolio_initiative_description}    Criada pela suíte de interface do Portfolio.
${portfolio_wip_limit}              999
${portfolio_block_reason}           Aguardando decisão do comitê.
${tactical_cadeia_base}             Robot UI Cadeia TC
${tactical_cadeia_label}            Cadeia
${tactical_feature_base}            Robot UI Feature
# Títulos das telas = nomes dos níveis da instalação padrão (GET /org-levels; Proposta 06).
${portfolio_level_name}             Portfólio
${tactical_level_name}              Cadeia
${squad_level_name}                 Squad
${squad_unit_base}                  Robot UI Squad SQ
${squad_unit_label}                 Squad
${squad_task_base}                  Robot UI Task
${squad_sprint_base}                Robot UI Sprint
${portfolio_document_text}           Texto escrito pela suíte de interface.
${portfolio_renamed_suffix}          (renomeada)
${gate_ui_name}                 Robot Gate UI
${gate_ui_message}              Aguardando a aprovação de arquitetura.
${gate_ui_path}                 Ideia → Cancelado
${gate_ui_to_key}               CANCELLED
${gate_ui_to_name}              Cancelado
