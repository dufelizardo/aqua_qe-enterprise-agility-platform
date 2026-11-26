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
          // Gates por transição do workflow (SSD 5; #78).
          {
            path: 'gates',
            title: 'Gates · AQuA-QE',
            loadComponent: () => import('./admin/admin-gates').then((m) => m.AdminGates),
          },
          // Responsável padrão por unidade e tipo (#43).
          {
            path: 'responsavel-padrao',
            title: 'Responsável padrão · AQuA-QE',
            loadComponent: () => import('./admin/admin-default-owner').then((m) => m.AdminDefaultOwner),
          },
        ],
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
