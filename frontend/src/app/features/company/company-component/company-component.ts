import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { CompanyService } from '../../../core/services/company-service';
import { NotificationService } from '../../../core/services/notification-service';
import { Company } from '../../models/company-model';
import { CompanyFormComponent } from '../company-form-component/company-form-component';

@Component({
  standalone: true,
  imports: [CommonModule, CompanyFormComponent],
  selector: 'app-company-component',
  styleUrl: './company-component.scss',
  templateUrl: './company-component.html',
})
export class CompanyComponent implements OnInit {
  private readonly companyService = inject(CompanyService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);

  company: Company | null = null;
  isLoading = false;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading = true;

    this.companyService.find().subscribe({
      next: company => {
        this.company = company;
        this.isLoading = false;
      },
      error: error => {
        this.company = null;
        this.isLoading = false;

        if (error?.status !== 404) {
          this.notification.error(
            error?.error?.message ??
              'Não foi possível carregar os dados da empresa.',
          );
        }
      },
    });
  }

  onSaved(company: Company): void {
    this.company = company;
  }

  openFiscal(): void {
    void this.router.navigate(['/configuracoes/fiscal']);
  }
}
