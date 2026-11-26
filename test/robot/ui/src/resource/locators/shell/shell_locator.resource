*** Comments ***
    Locators da estrutura da aplicação (cabeçalho, menu e home). Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${SHELL_PAGE_TITLE}         css=[data-testid="page-title"]
${SHELL_WELCOME}            css=[data-testid="welcome"]
${SHELL_PROFILE_BUTTON}     css=[data-testid="profile-button"]
${SHELL_PROFILE_NAME}       css=[data-testid="profile-name"]
${SHELL_LOGOUT}             css=[data-testid="logout"]
${SHELL_BRAND}              css=[data-testid="brand"]
# Home: recentes e novidades (#83).
${HOME_RECENT_ITEM_BY_TEXT}     css=[data-testid="home-recent-item"] a:text-is("{}")
${HOME_RECENT}                  css=[data-testid="home-recent"]
${HOME_NEWS}                    css=[data-testid="home-news"]
# Busca (#84).
${SHELL_SEARCH_INPUT}           css=[data-testid="search-input"]
${SEARCH_RESULT_LINK_BY_TEXT}   css=[data-testid="search-result-link"]:has-text("{}")
${SEARCH_GROUP_PAGE}            css=[data-testid="search-group-PAGE"]
${HOME_FAVORITES}               css=[data-testid="home-favorites"]
${HOME_RECENT_LOADED}           css=[data-testid="home-recent"] :is([data-testid="home-recent-item"], [data-testid="home-recent-empty"]) >> nth=0
${HOME_NEWS_LOADED}             css=[data-testid="home-news"] :is([data-testid="home-news-item"], [data-testid="home-news-empty"]) >> nth=0
${HOME_FAVORITES_LOADED}        css=[data-testid="home-favorites"] :is([data-testid="home-favorite-item"], [data-testid="home-favorites-empty"]) >> nth=0
