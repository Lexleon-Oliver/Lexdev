import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { CompanyService } from '../../../core/services/company-service';
import { Company, CompanyRequest } from '../../models/company-model';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  selector: 'app-company-component',
  styleUrl: './company-component.scss',
  templateUrl: './company-component.html',
})
export class CompanyComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly companyService = inject(CompanyService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);

  company: Company | null = null;
  isLoading = false;
  isSaving = false;

  readonly form = this.fb.group({
    name: ['', Validators.required],
    cpfCnpj: ['', [Validators.required, Validators.pattern(/^\d{14}$/)]],
    nomeFantasia: [''],
    inscricaoEstadual: [''],
    email: [''],
    telefone: [''],
    cep: [''],
    logradouro: [''],
    numero: [''],
    complemento: [''],
    bairro: [''],
    cidade: [''],
    uf: [''],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.companyService.find().subscribe({
      next: company => {
        this.company = company;
        this.patch(company);
        this.isLoading = false;
      },
      error: error => {
        this.company = null;
        this.isLoading = false;
        if (error?.status !== 404) {
          this.notification.error(error?.error?.message ?? 'Não foi possível carregar os dados da empresa.');
        }
      },
    });
  }

  private patch(company: Company): void {
    const email = company.contacts.find(x => x.type === 'EMAIL')?.value ?? '';
    const phone = company.contacts.find(x => x.principal && x.type !== 'EMAIL')?.value
      ?? company.contacts.find(x => x.type !== 'EMAIL')?.value
      ?? '';
    const address = company.addresses.find(x => x.principal) ?? company.addresses[0];

    this.form.reset({
      name: company.person.name ?? '',
      cpfCnpj: company.person.cpfCnpj ?? '',
      nomeFantasia: company.legalEntity?.nomeFantasia ?? '',
      inscricaoEstadual: company.legalEntity?.inscricaoEstadual ?? '',
      email,
      telefone: phone,
      cep: address?.cep ?? '',
      logradouro: address?.logradouro ?? '',
      numero: address?.numero ?? '',
      complemento: address?.complemento ?? '',
      bairro: address?.bairro ?? '',
      cidade: address?.cidade ?? '',
      uf: address?.uf ?? '',
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show('Preencha a razão social e um CNPJ válido.', 'warning');
      return;
    }

    const v = this.form.getRawValue();
    const email = String(v.email ?? '').trim();
    const telefone = String(v.telefone ?? '').replace(/\D/g, '');
    const request: CompanyRequest = {
      person: {
        cpfCnpj: String(v.cpfCnpj ?? '').replace(/\D/g, ''),
        tipoPessoa: 'PJ',
        name: String(v.name ?? '').trim(),
      },
      legalEntity: {
        nomeFantasia: String(v.nomeFantasia ?? '').trim() || null,
        inscricaoEstadual: String(v.inscricaoEstadual ?? '').replace(/\D/g, '') || null,
      },
      contacts: [
        ...(email ? [{ type: 'EMAIL', value: email, principal: true }] : []),
        ...(telefone ? [{ type: 'TELEFONE', value: telefone, principal: !email }] : []),
      ],
      addresses: [{
        type: 'COMERCIAL',
        cep: String(v.cep ?? '').replace(/\D/g, '') || null,
        logradouro: String(v.logradouro ?? '').trim() || null,
        numero: String(v.numero ?? '').trim() || null,
        complemento: String(v.complemento ?? '').trim() || null,
        bairro: String(v.bairro ?? '').trim() || null,
        cidade: String(v.cidade ?? '').trim() || null,
        uf: String(v.uf ?? '').trim().toUpperCase() || null,
        principal: true,
      }],
    };

    this.isSaving = true;
    this.companyService.save(request).subscribe({
      next: company => {
        this.company = company;
        this.patch(company);
        this.isSaving = false;
        this.notification.success('Dados da empresa salvos.');
      },
      error: error => {
        this.isSaving = false;
        this.notification.error(error?.error?.message ?? 'Não foi possível salvar os dados da empresa.');
      },
    });
  }

  openFiscal(): void {
    void this.router.navigate(['/configuracoes/fiscal']);
  }
}
