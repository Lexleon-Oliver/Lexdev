import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Forbidden } from './core/pages/forbidden/forbidden';
import { NotFound } from './core/pages/not-found/not-found';
import { authGuard } from './core/guards/auth.guard';


export const routes: Routes = [
   { path: 'login', loadComponent: () => import('./features/auth/login/login').then(m => m.Login) },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/layout/dashboard-component/dashboard-component').then(m => m.DashboardComponent),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      { path: 'home', loadComponent: () => import('./features/dashboard/home/home-component/home-component').then(m => m.HomeComponent) },
      // Outras rotas filhas serão adicionadas depois
    ]
  },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: '403', component: Forbidden },
  { path: '404', component: NotFound },
  { path: '**', redirectTo: '404' }
];
