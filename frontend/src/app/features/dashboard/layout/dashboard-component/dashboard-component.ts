import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterModule, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../../core/services/auth-service';
import { MenuItem } from '../../../models/menu-item';

@Component({
  imports: [CommonModule, RouterModule, RouterOutlet],
  selector: 'app-dashboard-component',
  styleUrl: './dashboard-component.scss',
  templateUrl: './dashboard-component.html',
})
export class DashboardComponent implements OnInit {
  authService = inject(AuthService);
  private router = inject(Router);
  profileMenuOpen = false;

   menus: MenuItem[] = [
  {
    key: 'inicio',
    label: 'Início',
    icon: 'fas fa-home',
    route: ['/dashboard']
  },

  {
    key: 'cadastros',
    label: 'Cadastros',
    icon: 'fas fa-database',
    open: false,

    submenu: [
      {
        key: 'clientes',
        label: 'Clientes',
        icon: '',
        route: ['/clientes']
      },
      {
        key: 'fornecedores',
        label: 'Fornecedores',
        icon: '',
        route: ['/fornecedores']
      },
      {
        key: 'produtos',
        label: 'Produtos',
        icon: '',
        route: ['/produtos']
      }
    ]
  },

  {
    key: 'relatorios',
    label: 'Relatórios',
    icon: 'fas fa-chart-bar',
    open: false,

    submenu: [
      {
        key: 'vendas',
        label: 'Vendas',
        icon: '',
        route: ['/vendas']
      },
      {
        key: 'estoque',
        label: 'Estoque',
        icon: '',
        route: ['/estoque']
      },
      {
        key: 'financeiro',
        label: 'Financeiro',
        icon: '',
        route: ['/financeiro']
      }
    ]
  },

  {
    key: 'configuracoes',
    label: 'Configurações',
    icon: 'fas fa-cog',
    route: ['/configuracoes']
  }
];

  sidebarMobileOpen = false;

  toggleSubmenu(menu: MenuItem, event: Event): void {
    event.preventDefault();

    menu.open = !menu.open;
  }

  ngOnInit(): void {
    // Busca os dados do banco dinamicamente via GET /api/users/me
    this.authService.fetchCurrentUser().subscribe({
      error: () => this.onLogout() // Se o token for inválido, desloga o usuário
    });
  }



  toggleProfileMenu() {
    this.profileMenuOpen = !this.profileMenuOpen;
  }

  goToChangePassword() {
    this.profileMenuOpen = false;
    // Navegar para a rota de alteração de senha
    this.router.navigate(['/change-password']);
  }

  onLogout(): void {
    this.authService.logout();
  }

  toggleSidebarMobile(): void {
    this.sidebarMobileOpen = !this.sidebarMobileOpen;
  }
}
