/** Como cada tela de Flight Level usa o quadro (UX/UI 7.3, 7.5 e 7.7). */
export interface LevelSetup {
  level: 'FL1' | 'FL2' | 'FL3';
  title: string;
  lead: string;
  /** Plural usado em mensagens e estados vazios ("Nenhuma Iniciativa."). */
  emptyItems: string;
  newLabel: string;
  /** Tipos de artefato que a tela cria (SSD 8.1). */
  creatableTypes: string[];
  /** Papéis que criam e editam no nível (matriz do SSD 2). Só decide o que mostrar; quem decide é a API. */
  createRoles: string[];
  /** Nome da unidade do nível, no formulário ("Portfólio", "Cadeia", "Squad"). */
  unitLabel: string;
  boardLabel: string;
  childrenLabel: string;
}

const MANAGE_ROLES_NOTE = 'Quem gere quadros (PM, Tech Lead ou administrador) pode criá-lo.';

export const LEVEL_SETUPS: Record<'portfolio' | 'tactical' | 'squad', LevelSetup> = {
  portfolio: {
    level: 'FL3',
    title: 'Portfolio',
    lead: 'As Iniciativas do workspace no fluxo estratégico.',
    emptyItems: 'Nenhuma Iniciativa',
    newLabel: 'Nova Iniciativa',
    creatableTypes: ['INITIATIVE'],
    createRoles: ['PLATFORM_ADMIN', 'BUSINESS_OWNER', 'PRODUCT_MANAGER'],
    unitLabel: 'Portfólio',
    boardLabel: 'quadro de portfólio',
    childrenLabel: 'Features',
  },
  tactical: {
    level: 'FL2',
    title: 'Tactical',
    lead: 'As Features e Features de Sustentação do workspace no fluxo tático.',
    emptyItems: 'Nenhuma Feature',
    newLabel: 'Nova Feature',
    creatableTypes: ['FEATURE', 'SUSTAINING_FEATURE'],
    createRoles: ['PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD'],
    unitLabel: 'Cadeia',
    boardLabel: 'quadro tático',
    childrenLabel: 'Itens',
  },
  squad: {
    level: 'FL1',
    title: 'Squad',
    lead: 'Os itens operacionais do workspace: Histórias, Spikes, Débitos, Bugs, Sustentação e Tasks.',
    emptyItems: 'Nenhum item',
    newLabel: 'Novo item',
    creatableTypes: ['STORY', 'SPIKE', 'TECH_DEBT', 'BUG', 'PRODUCTION_ISSUE', 'MAINTENANCE', 'TASK', 'SUBTASK'],
    createRoles: ['PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD', 'UX_DESIGNER', 'QA_ENGINEER', 'SQUAD_MEMBER'],
    unitLabel: 'Squad',
    boardLabel: 'quadro da squad',
    childrenLabel: 'Subitens',
  },
};

export { MANAGE_ROLES_NOTE };
