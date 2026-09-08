import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  imports: [],
  selector: 'app-dashboard-component',
  styleUrl: './dashboard-component.scss',
  templateUrl: './dashboard-component.html',
})
export class DashboardComponent {
  private router = inject(Router);

  logout(): void {
    localStorage.removeItem('token');
    this.router.navigate(['/login']);
  }
}
