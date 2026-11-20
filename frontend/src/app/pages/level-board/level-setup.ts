import { FlightLevel } from '../../core/levels.service';

/**
 * Como cada tela de Flight Level usa o quadro (UX/UI 7.3, 7.5 e 7.7). Nenhum nome de nível organizacional fica aqui:
 * o título da tela e o rótulo da unidade vêm da configuração (GET /org-levels; Proposta 06, 14.1), e o rótulo do botão
 * de criação vem do nome do tipo de artefato (GET /artifact-types).
 */
export interface LevelSetup {
  level: FlightLevel;
  /** Descrição do Flight Level, que é conceito de gestão e não muda por instalação. */
  lead: string;
  /** Tipos de artefato que a tela cria, na ordem em que aparecem (SSD 8.1). */
  creatableTypes: string[];
  /** Papéis que criam e editam no nível (matriz do SSD 2). Só decide o que mostrar; quem decide é a API. */
  createRoles: string[];
}

export const MANAGE_ROLES_NOTE = 'Quem gere quadros (PM, Tech Lead ou administrador) pode criá-lo.';

export const LEVEL_SETUPS: Record<'portfolio' | 'tactical' | 'squad', LevelSetup> = {
  portfolio: {
    level: 'FL3',
    lead: 'Trabalho estratégico (FL3) do workspace.',
    creatableTypes: ['INITIATIVE'],
    createRoles: ['PLATFORM_ADMIN', 'BUSINESS_OWNER', 'PRODUCT_MANAGER'],
  },
  tactical: {
    level: 'FL2',
    lead: 'Trabalho tático (FL2) do workspace.',
    creatableTypes: ['FEATURE', 'SUSTAINING_FEATURE'],
    createRoles: ['PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD'],
  },
  squad: {
    level: 'FL1',
    lead: 'Trabalho operacional (FL1) do workspace.',
    creatableTypes: ['STORY', 'SPIKE', 'TECH_DEBT', 'BUG', 'PRODUCTION_ISSUE', 'MAINTENANCE', 'TASK', 'SUBTASK'],
    createRoles: ['PLATFORM_ADMIN', 'PRODUCT_MANAGER', 'TECH_LEAD', 'UX_DESIGNER', 'QA_ENGINEER', 'SQUAD_MEMBER'],
  },
};
