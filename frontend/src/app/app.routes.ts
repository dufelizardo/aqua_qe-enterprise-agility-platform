import { Routes } from '@angular/router';

import { adminGuard } from './core/admin.guard';
import { authGuard } from './core/auth.guard';
import { workspaceGuard } from './core/workspace.service';
import { confirmLeave } from './knowledge/draft-store';

/** Rotas do MVP (UX/UI, seção 5). A landing é pública; o restante exige sessão. */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'AQuA-QE Enterprise Agility Platform',
    loadComponent: () => import('./pages/landing/landing').then((m) => m.Landing),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      {
        path: 'home',
        title: 'Meu trabalho · AQuA-QE',
        loadComponent: () => import('./pages/home/home').then((m) => m.Home),
      },
      {
        path: 'workspace',
        title: 'Workspace · AQuA-QE',
        loadComponent: () => import('./pages/workspace/workspace-select').then((m) => m.WorkspaceSelect),
      },
      // Portfolio (FL3), Tactical (FL2) e Squad (FL1) usam o mesmo quadro configurável (UX/UI 7.3, 7.5 e 7.7).
      {
        path: 'portfolio',
        title: 'Portfolio · AQuA-QE',
        canActivate: [workspaceGuard],
        loadComponent: () => import('./pages/level-board/level-board').then((m) => m.LevelBoard),
        data: { screen: 'portfolio' },
      },
      {
        path: 'tactical',
        title: 'Tactical · AQuA-QE',
        canActivate: [workspaceGuard],
        loadComponent: () => import('./pages/level-board/level-board').then((m) => m.LevelBoard),
        data: { screen: 'tactical' },
      },
      {
        path: 'squad',
        title: 'Squad · AQuA-QE',
        canActivate: [workspaceGuard],
        loadComponent: () => import('./pages/level-board/level-board').then((m) => m.LevelBoard),
        data: { screen: 'squad' },
      },
      // Wiki da unidade ativa (Proposta 07, Fase A; UX/UI 7.12).
      {
        path: 'wiki',
        title: 'Wiki · AQuA-QE',
        canActivate: [workspaceGuard],
        canDeactivate: [confirmLeave],
        loadComponent: () => import('./pages/wiki/wiki').then((m) => m.Wiki),
        data: { mode: 'wiki' },
      },
      // Regimento da unidade, independente do Wiki (bug #66, 2026-11-21).
      {
        path: 'regimento',
        title: 'Regimento · AQuA-QE',
        canActivate: [workspaceGuard],
        canDeactivate: [confirmLeave],
        loadComponent: () => import('./pages/wiki/wiki').then((m) => m.Wiki),
        data: { mode: 'regimento' },
      },
      // Página do artefato: Visão geral e, na Iniciativa e na Feature, os documentos PRD, SAD, SSD, UX/UI e QA (#63).
      {
        path: 'artefato/:key',
        title: 'Artefato · AQuA-QE',
        canDeactivate: [confirmLeave],
        loadComponent: () => import('./pages/artifact/artifact-page').then((m) => m.ArtifactPage),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'organizacao' },
          {
            path: 'organizacao',
            title: 'Estrutura organizacional · AQuA-QE',
            loadComponent: () => import('./admin/admin-organization').then((m) => m.AdminOrganization),
          },
          // Workflow Designer: workflow próprio por unidade, versões e migração explícita (#49; UI-26).
          {
            path: 'workflows',
            title: 'Workflows · AQuA-QE',
            loadComponent: () => import('./admin/admin-workflows').then((m) => m.AdminWorkflows),
          },
          // Catálogo de templates de quadro (#50; MDL-07).
          {
            path: 'templates-de-quadro',
            title: 'Templates de quadro · AQuA-QE',
            loadComponent: () => import('./admin/admin-board-templates').then((m) => m.AdminBoardTemplates),
          },
          // Gates por transição do workflow (SSD 5; #78).
          {
            path: 'gates',
            title: 'Gates · AQuA-QE',
            loadComponent: () => import('./admin/admin-gates').then((m) => m.AdminGates),
          },
          // Campos customizados por tipo de artefato (#17; REV-12).
          {
            path: 'campos-customizados',
            title: 'Campos customizados · AQuA-QE',
            loadComponent: () => import('./admin/admin-custom-fields').then((m) => m.AdminCustomFields),
          },
          // Pessoas e privacidade: exportar e anonimizar dados pessoais (#32; REV-14).
          {
            path: 'pessoas',
            title: 'Pessoas e privacidade · AQuA-QE',
            loadComponent: () => import('./admin/admin-people').then((m) => m.AdminPeople),
          },
          // Responsável padrão por unidade e tipo (#43).
          {
            path: 'responsavel-padrao',
            title: 'Responsável padrão · AQuA-QE',
            loadComponent: () => import('./admin/admin-default-owner').then((m) => m.AdminDefaultOwner),
          },
        ],
      },
      // Preferências pessoais: tema e avisos (#15; REV-10).
      {
        path: 'preferencias',
        title: 'Preferências · AQuA-QE',
        loadComponent: () => import('./pages/preferences/preferences').then((m) => m.PreferencesPage),
      },
      {
        path: 'busca',
        title: 'Busca · AQuA-QE',
        loadComponent: () => import('./pages/search/search').then((m) => m.Search),
      },
    ],
  },
  { path: '**', redirectTo: 'home' },
];
