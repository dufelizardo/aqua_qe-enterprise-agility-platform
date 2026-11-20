import { Routes } from '@angular/router';

import { adminGuard } from './core/admin.guard';
import { authGuard } from './core/auth.guard';
import { workspaceGuard } from './core/workspace.service';

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
      {
        path: 'portfolio',
        title: 'Portfolio · AQuA-QE',
        canActivate: [workspaceGuard],
        loadComponent: () => import('./pages/portfolio/portfolio').then((m) => m.Portfolio),
      },
      {
        path: 'tactical',
        title: 'Tactical · AQuA-QE',
        loadComponent: () => import('./pages/placeholder/placeholder').then((m) => m.Placeholder),
        data: { screen: 'Tactical Board', level: 'FL2' },
      },
      {
        path: 'squad',
        title: 'Squad · AQuA-QE',
        loadComponent: () => import('./pages/placeholder/placeholder').then((m) => m.Placeholder),
        data: { screen: 'Squad Workspace', level: 'FL1' },
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
