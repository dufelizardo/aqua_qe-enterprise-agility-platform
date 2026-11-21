*** Comments ***
    Locators do menu lateral com a árvore organizacional (Proposta 06, Revisão 2; PROPOSAL-07). Usam data-testid
    (doc/QA.md, seção 8).

*** Variables ***
${NAV_SIDEBAR}                  css=[data-testid="sidebar"]
${NAV_MENU_TOGGLE}              css=[data-testid="menu-toggle"]
${NAV_ORG_TREE}                 css=[data-testid="org-tree-nav"]
${NAV_ORG_TREE_SEARCH}          css=[data-testid="org-tree-search"]
${NAV_WORKSPACE_CURRENT}        css=[data-testid="workspace-current"]
${NAV_CONTEXT_TITLE}            css=[data-testid="nav-context"]
${NAV_VIEW_PORTFOLIO}           css=[data-testid="nav-portfolio"]
${NAV_VIEW_TACTICAL}            css=[data-testid="nav-tactical"]
${NAV_VIEW_SQUAD_BACKLOG}       css=[data-testid="nav-squad-backlog"]
${NAV_VIEW_SQUAD_SPRINTS}       css=[data-testid="nav-squad-sprints"]
${NAV_SQUAD_BACKLOG_TAB}        css=[data-testid="tab-backlog"][aria-selected="true"]
# Unidade escolhível pelo nome exato: format com o nome.
${NAV_ORG_NODE_BY_NAME}         css=[data-testid="org-node"]:text-is("{}")
# Botão de expandir ou recolher da unidade com esse nome.
${NAV_ORG_TOGGLE_BY_NAME}       css=[role="treeitem"]:has([data-testid="org-node"]:text-is("{}")) [data-testid="org-node-toggle"]
