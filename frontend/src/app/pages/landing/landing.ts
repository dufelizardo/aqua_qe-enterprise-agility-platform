import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { SessionService } from '../../core/session.service';

/**
 * Landing pública, no visual do protótipo (doc/prototype/AQuA-QE Project.dc.html). Todo texto vem do README e do
 * PRD; não há números, clientes ou depoimentos, e o roteiro aparece como proposta (PRD, seção 13).
 */
@Component({
  selector: 'app-landing',
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly repositoryUrl = 'https://github.com/dufelizardo/aqua_qe-enterprise-agility-platform';

  protected readonly levels = [
    { code: 'FL3', name: 'Estratégico', who: 'Portfólios', artifact: 'Iniciativa (3 meses a 1 ano)' },
    { code: 'FL2', name: 'Tático', who: 'Cadeias de valor', artifact: 'Feature e Feature de Sustentação (até 3 meses)' },
    { code: 'FL1', name: 'Operacional', who: 'Squads', artifact: 'História, Bug, Spike, Débito Técnico, Task' },
  ];

  protected readonly ideas = [
    {
      title: 'Documentação viva no artefato',
      text: 'PRD, arquitetura, especificação, UX/UI e plano de testes ficam em abas da própria Iniciativa ou Feature, versionados, e não em uma wiki separada.',
    },
    {
      title: 'Governança por gates',
      text: 'O sistema impede a mudança de status enquanto campos, documentos ou aprovações exigidos não estiverem prontos, e mostra exatamente o que falta.',
    },
    {
      title: 'Contexto herdado',
      text: 'Quem trabalha em uma História vê na mesma tela a Feature, a Iniciativa e as regras de negócio que a originaram.',
    },
  ];

  protected readonly roadmap = [
    {
      phase: 'MVP',
      text: 'Hierarquia FL3–FL1 e quadros; workspaces de Iniciativa e de Feature com documentação versionada; gates por campo, documento e aprovação; login com SSO e MFA; papéis por unidade organizacional; trilha de auditoria.',
    },
    {
      phase: 'Fase 2',
      text: 'Dependências entre portfólios, OKRs, capacity planning, gestão de testes e rastreabilidade, integração com Git e CI/CD, métricas de fluxo e painéis padrão.',
    },
    {
      phase: 'Fase 3',
      text: 'Construtor de dashboards, motor de customização completo, instalação air-gapped e análises assistidas por IA.',
    },
  ];

  protected enter(): void {
    if (this.session.authenticated()) {
      void this.router.navigate(['/home']);
    } else {
      this.session.login();
    }
  }
}
