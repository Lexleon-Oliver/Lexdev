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
  sidebarMobileOpen = false;

  menus: MenuItem[] = [

  ];

  toggleSubmenu(menu: MenuItem, event: Event): void {
    event.preventDefault();

    menu.open = !menu.open;
  }

  ngOnInit(): void {
    this.authService.fetchCurrentUser().subscribe({
      next: () => { this.buildMenus(); },
      error: () => { this.onLogout(); }
    });
  }

  private buildMenus(): void {
    this.menus = [
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
    if (this.authService.hasRole(['ROLE_ADMIN', 'ROLE_SUPPORT'])) {
      this.menus.push({
        key: 'admin',
        label: 'Administração',
        icon: 'fas fa-cogs',
        open: false,
        submenu: [
          {
            key: 'users',
            label: 'Gerenciar Usuários',
            icon: '',
            route: ['/usuarios']
          }
        ]
      });
    }
  }


  toggleProfileMenu() {
    this.profileMenuOpen = !this.profileMenuOpen;
  }

  goToChangePassword() {
    this.router.navigate(['/alterar-senha']);
  }

  onLogout(): void {
    this.authService.logout();
  }

  toggleSidebarMobile(): void {
    this.sidebarMobileOpen = !this.sidebarMobileOpen;
  }
}
