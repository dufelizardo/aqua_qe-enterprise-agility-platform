*** Comments ***
    Locators da Administração › Estrutura organizacional. Usam data-testid (doc/QA.md, seção 8).

*** Variables ***
${ADMIN_NAV_LINK}               css=[data-testid="nav-admin"]
${ADMIN_ORG_TREE}               css=[data-testid="org-tree"]
${ADMIN_ORG_DETAIL_NAME}        css=[data-testid="org-detail-name"]
${ADMIN_ORG_RENAME_INPUT}       css=[data-testid="org-rename-input"]
${ADMIN_ORG_RENAME_SUBMIT}      css=[data-testid="org-rename-submit"]
${ADMIN_ORG_NEW_NAME}           css=[data-testid="org-new-name"]
${ADMIN_ORG_NEW_LEVEL}          css=[data-testid="org-new-level"]
${ADMIN_ORG_NEW_SUBMIT}         css=[data-testid="org-new-submit"]
${ADMIN_ORG_LINK_TARGET}        css=[data-testid="org-link-target"]
${ADMIN_ORG_LINK_SUBMIT}        css=[data-testid="org-link-submit"]
${ADMIN_ORG_SERVES_LIST}        css=[data-testid="org-serves-list"]
# Item da árvore pelo nome exato da unidade: format com o nome.
${ADMIN_ORG_TREE_ITEM_BY_NAME}  css=[data-testid="org-tree-item"]:has(.unit-name:text-is("{}"))
