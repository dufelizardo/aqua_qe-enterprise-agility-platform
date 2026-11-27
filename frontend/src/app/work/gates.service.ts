import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/** Tipo de regra de gate (SSD 5.2). */
export type RuleType = 'REQUIRED_FIELD' | 'DOCUMENT_SECTION_FILLED' | 'DOCUMENT_APPROVED' | 'MIN_CHILDREN' | 'ROLE_APPROVAL';

export interface LastDecision {
  decision: 'APPROVED' | 'REJECTED';
  comment: string | null;
  by: { id: string; name: string | null };
  at: string;
}

/** Regra no painel de gates do artefato (UX/UI 6.4). */
export interface PanelRule {
  ruleId: string;
  type: RuleType;
  category: 'VALIDATION' | 'APPROVAL';
  message: string;
  params: Record<string, unknown>;
  satisfied: boolean;
  lastDecision: LastDecision | null;
  canDecide: boolean;
}

/** Gate de uma transição que sai do status atual. */
export interface PanelGate {
  gateId: string;
  name: string;
  transitionId: string;
  to: { key: string; name: string };
  satisfied: number;
  total: number;
  rules: PanelRule[];
}

/** Regra na configuração do gate (PUT /workflow-transitions/{id}/gate). */
export interface RuleDraft {
  type: RuleType;
  params: Record<string, unknown>;
  message: string;
}

export interface GateConfig {
  id: string;
  name: string;
  transitionId: string;
  from: { key: string; name: string };
  to: { key: string; name: string };
  workflow: string;
  artifactTypes: string[];
  rules: (RuleDraft & { id: string; category: string })[];
}

export interface GateSummary {
  id: string;
  transitionId: string;
  name: string;
  rules: number;
}

/** Workflow com status e transições (GET /workflows). */
export interface WorkflowInfo {
  id: string;
  name: string;
  artifactTypes: string[];
  /** Unidade dona do workflow (#49): a raiz para os padrões. */
  orgUnit?: { id: string; name: string } | null;
  statuses: { id: string; key: string; name: string }[];
  transitions: { id: string; from: string; to: string }[];
}

export const RULE_LABELS: Record<RuleType, string> = {
  REQUIRED_FIELD: 'Campo preenchido',
  DOCUMENT_SECTION_FILLED: 'Seção do documento preenchida',
  DOCUMENT_APPROVED: 'Documento aprovado',
  MIN_CHILDREN: 'Mínimo de itens filhos',
  ROLE_APPROVAL: 'Aprovação de um papel',
};

export const FIELD_LABELS: Record<string, string> = {
  description: 'Descrição',
  ownerId: 'Responsável',
  technicalLeadId: 'Tech Lead',
  qaOwnerId: 'QA',
  assigneeId: 'Atribuído a',
  size: 'Estimativa',
};

export const ROLE_LABELS: Record<string, string> = {
  BUSINESS_OWNER: 'Business Owner',
  PRODUCT_MANAGER: 'Product Manager',
  TECH_LEAD: 'Tech Lead',
  UX_DESIGNER: 'UX Designer',
  QA_ENGINEER: 'QA',
  SQUAD_MEMBER: 'Membro da squad',
  PLATFORM_ADMIN: 'Administrador',
};

/** Parâmetros vazios de cada tipo, para o formulário de configuração. */
export function emptyParams(type: RuleType): Record<string, unknown> {
  switch (type) {
    case 'REQUIRED_FIELD':
      return { field: 'size' };
    case 'DOCUMENT_SECTION_FILLED':
      return { documentType: 'PRD', sectionKey: '' };
    case 'DOCUMENT_APPROVED':
      return { documentType: 'PRD' };
    case 'MIN_CHILDREN':
      return { childType: 'FEATURE', min: 1 };
    case 'ROLE_APPROVAL':
      return { role: 'TECH_LEAD', label: '' };
  }
}

/** Resumo legível da regra: "Estimativa", "PRD aprovado", "Aprovação de Tech Lead"… */
export function ruleSummary(rule: { type: RuleType; params: Record<string, unknown> }): string {
  const p = rule.params;
  switch (rule.type) {
    case 'REQUIRED_FIELD': {
      const field = String(p['field'] ?? '');
      return `Campo: ${FIELD_LABELS[field] ?? field.replace(/^customFields\./, '')}`;
    }
    case 'DOCUMENT_SECTION_FILLED':
      return `${String(p['documentType'])}: seção ${String(p['sectionKey'])}`;
    case 'DOCUMENT_APPROVED':
      return `${String(p['documentType'])} aprovado`;
    case 'MIN_CHILDREN':
      return `Ao menos ${String(p['min'])} ${String(p['childType'])}`;
    case 'ROLE_APPROVAL': {
      const role = String(p['role'] ?? '');
      return p['label'] ? `${String(p['label'])} (${ROLE_LABELS[role] ?? role})` : `Aprovação de ${ROLE_LABELS[role] ?? role}`;
    }
  }
}

const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class GatesService {
  private readonly http = inject(HttpClient);

  async panel(artifact: string): Promise<PanelGate[]> {
    return (await firstValueFrom(this.http.get<{ items: PanelGate[] }>(`${API}/artifacts/${artifact}/gates`))).items;
  }

  async decide(artifact: string, ruleId: string, decision: 'APPROVED' | 'REJECTED', comment: string | null): Promise<PanelGate[]> {
    return (
      await firstValueFrom(
        this.http.post<{ items: PanelGate[] }>(`${API}/artifacts/${artifact}/approvals`, { gateRuleId: ruleId, decision, comment }),
      )
    ).items;
  }

  async workflows(): Promise<WorkflowInfo[]> {
    return (await firstValueFrom(this.http.get<{ items: WorkflowInfo[] }>(`${API}/workflows`))).items;
  }

  async gates(): Promise<GateSummary[]> {
    return (await firstValueFrom(this.http.get<{ items: GateSummary[] }>(`${API}/gates`))).items;
  }

  gate(transitionId: string): Promise<GateConfig> {
    return firstValueFrom(this.http.get<GateConfig>(`${API}/workflow-transitions/${transitionId}/gate`));
  }

  save(transitionId: string, name: string, rules: RuleDraft[]): Promise<GateConfig> {
    return firstValueFrom(this.http.put<GateConfig>(`${API}/workflow-transitions/${transitionId}/gate`, { name, rules }));
  }

  remove(transitionId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${API}/workflow-transitions/${transitionId}/gate`));
  }
}
