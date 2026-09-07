import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Forbidden } from './core/pages/forbidden/forbidden';
import { NotFound } from './core/pages/not-found/not-found';
import { DashboardComponent } from './features/auth/login/dashboard/dashboard-component/dashboard-component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard] // <-- Protege o acesso direto
  },
  { path: 'login', component: Login },
  { path: '403', component: Forbidden },
  { path: '404', component: NotFound },
  { path: '**', redirectTo: '404' }
];
