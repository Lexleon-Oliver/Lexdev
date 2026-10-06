import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  inject,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { CompanyService } from '../../../core/services/company-service';
import { NotificationService } from '../../../core/services/notification-service';
import { Company, CompanyRequest } from '../../models/company-model';

@Component({
  standalone: true,
  selector: 'app-company-form',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './company-form-component.html',
  styleUrl: './company-form-component.scss',
})
export class CompanyFormComponent implements OnChanges {
  private readonly fb = inject(FormBuilder);
  private readonly companyService = inject(CompanyService);
  private readonly notification = inject(NotificationService);

  @Input() company: Company | null = null;
  @Output() saved = new EventEmitter<Company>();

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

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['company']) {
      if (this.company) {
        this.patch(this.company);
      } else {
        this.reset();
      }
    }
  }

  private patch(company: Company): void {
    const email = company.contacts.find(x => x.type === 'EMAIL')?.value ?? '';
    const phone =
      company.contacts.find(x => x.principal && x.type !== 'EMAIL')?.value ??
      company.contacts.find(x => x.type !== 'EMAIL')?.value ??
      '';
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

  private reset(): void {
    this.form.reset({
      name: '',
      cpfCnpj: '',
      nomeFantasia: '',
      inscricaoEstadual: '',
      email: '',
      telefone: '',
      cep: '',
      logradouro: '',
      numero: '',
      complemento: '',
      bairro: '',
      cidade: '',
      uf: '',
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show(
        'Preencha a razão social e um CNPJ válido.',
        'warning',
      );
      return;
    }

    const value = this.form.getRawValue();
    const email = String(value.email ?? '').trim();
    const telefone = String(value.telefone ?? '').replace(/\D/g, '');

    const request: CompanyRequest = {
      person: {
        cpfCnpj: String(value.cpfCnpj ?? '').replace(/\D/g, ''),
        tipoPessoa: 'PJ',
        name: String(value.name ?? '').trim(),
      },
      legalEntity: {
        nomeFantasia: String(value.nomeFantasia ?? '').trim() || null,
        inscricaoEstadual:
          String(value.inscricaoEstadual ?? '').replace(/\D/g, '') || null,
      },
      contacts: [
        ...(email
          ? [{ type: 'EMAIL', value: email, principal: true }]
          : []),
        ...(telefone
          ? [{ type: 'TELEFONE', value: telefone, principal: !email }]
          : []),
      ],
      addresses: [
        {
          type: 'COMERCIAL',
          cep: String(value.cep ?? '').replace(/\D/g, '') || null,
          logradouro: String(value.logradouro ?? '').trim() || null,
          numero: String(value.numero ?? '').trim() || null,
          complemento: String(value.complemento ?? '').trim() || null,
          bairro: String(value.bairro ?? '').trim() || null,
          cidade: String(value.cidade ?? '').trim() || null,
          uf: String(value.uf ?? '').trim().toUpperCase() || null,
          principal: true,
        },
      ],
    };

    this.isSaving = true;

    this.companyService.save(request).subscribe({
      next: company => {
        this.isSaving = false;
        this.patch(company);
        this.notification.success(
          this.company
            ? 'Dados da empresa atualizados.'
            : 'Empresa proprietária cadastrada com sucesso.',
        );
        this.saved.emit(company);
      },
      error: error => {
        this.isSaving = false;
        this.notification.error(
          error?.error?.message ??
            'Não foi possível salvar os dados da empresa.',
        );
      },
    });
  }
}
