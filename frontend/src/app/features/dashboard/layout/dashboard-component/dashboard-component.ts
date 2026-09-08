import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../../core/services/auth-service';
import { MenuItem } from '../../../models/menu-item';

@Component({
  imports: [CommonModule, RouterModule],
  selector: 'app-dashboard-component',
  styleUrl: './dashboard-component.scss',
  templateUrl: './dashboard-component.html',
})
export class DashboardComponent implements OnInit {

  isSidebarCollapsed = false;
  isMobileSidebarOpen = false;
  currentUser: string = '';

  // Tipagem explícita evita a inferência 'never[]' nos submenus
  menuItems: MenuItem[] = [
    {
      label: 'Dashboard',
      icon: 'bi-speedometer2',
      route: '/dashboard/home',
      children: []
    },
    {
      label: 'Cadastros',
      icon: 'bi-folder',
      route: null,
      children: [
        { label: 'Usuários', icon: 'bi-people', route: '/dashboard/users', children: [] },
        { label: 'Perfis', icon: 'bi-person-badge', route: '/dashboard/roles', children: [] },
        { label: 'Permissões', icon: 'bi-shield-lock', route: '/dashboard/permissions', children: [] }
      ]
    },
    {
      label: 'Relatórios',
      icon: 'bi-bar-chart',
      route: null,
      children: [
        { label: 'Vendas', icon: 'bi-graph-up', route: '/dashboard/reports/sales', children: [] },
        { label: 'Auditoria', icon: 'bi-journal-text', route: '/dashboard/reports/audit', children: [] }
      ]
    },
    {
      label: 'Configurações',
      icon: 'bi-gear',
      route: '/dashboard/settings',
      children: []
    }
  ];

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const user = this.authService.getUser();
    this.currentUser = user?.username || 'Usuário';
  }

  toggleSubmenu(item: MenuItem): void {
    item.expanded = !item.expanded;
  }

  toggleSidebar(): void {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  toggleMobileSidebar(): void {
    this.isMobileSidebarOpen = !this.isMobileSidebarOpen;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // Verifica se um item tem submenu ativo (para expansão)
  isSubmenuActive(item: any): boolean {
    if (!item.children || item.children.length === 0) return false;
    return item.children.some((child: any) => this.router.url.startsWith(child.route));
  }
}
