import { CommonModule } from '@angular/common';
import { Component, inject, Input } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Location } from '@angular/common';

@Component({
  imports: [CommonModule, RouterModule],
  selector: 'app-error-page-component',
  styleUrl: './error-page-component.scss',
  templateUrl: './error-page-component.html',
})
export class ErrorPageComponent {
   private route = inject(ActivatedRoute);
  private location = inject(Location);

  code = this.route.snapshot.data['code'] ?? '404';
  title = this.route.snapshot.data['title'] ?? 'Página não encontrada';
  message = this.route.snapshot.data['message']
    ?? 'A página que você está procurando não existe ou foi movida.';

  showBackButton = true;

  goBack(): void {
    this.location.back();
  }
}
