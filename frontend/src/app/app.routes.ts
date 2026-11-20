import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

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
        path: 'portfolio',
        title: 'Portfolio · AQuA-QE',
        loadComponent: () => import('./pages/placeholder/placeholder').then((m) => m.Placeholder),
        data: { screen: 'Portfolio Management', level: 'FL3' },
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
        path: 'busca',
        title: 'Busca · AQuA-QE',
        loadComponent: () => import('./pages/search/search').then((m) => m.Search),
      },
    ],
  },
  { path: '**', redirectTo: 'home' },
];
