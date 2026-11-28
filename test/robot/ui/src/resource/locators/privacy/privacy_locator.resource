*** Comments ***
    Locators de Administração › Pessoas e privacidade (#32). Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${PEOPLE_TAB}                   css=[data-testid="admin-tab-people"]
${PEOPLE_SEARCH}                css=[data-testid="people-search"]
${PEOPLE_SEARCH_SUBMIT}         css=[data-testid="people-search-submit"]
${PEOPLE_FIRST_ROW}             css=[data-testid="people-row"] >> nth=0
${PEOPLE_FIRST_EXPORT}          css=[data-testid="people-row"] >> nth=0 >> css=[data-testid="people-export"]
${PEOPLE_FIRST_ANONYMIZE}       css=[data-testid="people-row"] >> nth=0 >> css=[data-testid="people-anonymize"]
${PEOPLE_ANONYMIZE_NAME}        css=[data-testid="people-anonymize-name"]
${PEOPLE_ANONYMIZE_CONFIRM}     css=[data-testid="people-anonymize-confirm"]
${PEOPLE_ANONYMIZE_FORM}        css=[data-testid="people-anonymize-form"]
