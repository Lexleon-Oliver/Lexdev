import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { ErrorPageComponent } from './core/pages/error-page-component/error-page-component';
import { ChangePasswordComponent } from './core/pages/change-password-component/change-password-component';


export const routes: Routes = [

  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login')
        .then(m => m.Login)
  },

  {
    path: '',
    loadComponent: () =>
      import('./features/dashboard/layout/dashboard-component/dashboard-component')
        .then(m => m.DashboardComponent),

    canActivate: [authGuard],

    children: [

      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      },

      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/home/home-component/home-component')
            .then(m => m.HomeComponent)
      },
      { path: 'change-password', component: ChangePasswordComponent },
/*
      {
        path: 'clientes',
        loadComponent: () =>
          import('./features/clientes/clientes-component/clientes-component')
            .then(m => m.ClientesComponent)
      },

      {
        path: 'fornecedores',
        loadComponent: () =>
          import('./features/fornecedores/fornecedores-component/fornecedores-component')
            .then(m => m.FornecedoresComponent)
      }
*/
    ]
  },

   {
    path: '403',
    component: ErrorPageComponent,
    data: {
      code: '403',
      title: 'Acesso negado',
      message: 'Você não tem permissão para acessar esta página.'
    }
  },
  {
    path: '404',
    component: ErrorPageComponent,
    data: {
      code: '404',
      title: 'Página não encontrada',
      message: 'A página que você está procurando não existe ou foi movida.'
    }
  },
  // Rota coringa para redirecionar para 404
  { path: '**', redirectTo: '/404' }
];
