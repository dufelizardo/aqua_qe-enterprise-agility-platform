*** Comments ***
    Locators da tela Admin › Gates e dos Próximos passos da página do artefato (#78). Usam data-testid.

*** Variables ***
${GATES_WORKFLOW}               css=[data-testid="gates-workflow"]
${GATES_TRANSITION_BY_PATH}     css=[data-testid="gates-transition"]:has(.path:text-is("{}"))
${GATES_NAME}                   css=[data-testid="gates-name"]
${GATES_RULE_TYPE}              css=[data-testid="gates-rule-type"] >> nth=0
${GATES_RULE_MESSAGE}           css=[data-testid="gates-rule-message"] >> nth=0
${GATES_SAVE}                   css=[data-testid="gates-save"]
${GATES_REMOVE}                 css=[data-testid="gates-remove"]
${GATE_STEP}                    css=[data-testid="gate-step-{}"]
