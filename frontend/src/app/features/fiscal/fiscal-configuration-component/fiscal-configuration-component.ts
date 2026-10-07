import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { CompanyService } from '../../../core/services/company-service';
import { FiscalEstablishmentService } from '../../../core/services/fiscal-establishment-service';
import { NotificationService } from '../../../core/services/notification-service';
import { Company, CompanyRequest } from '../../models/company-model';
import {
  FiscalEnvironment,
  FiscalEstablishment,
  FiscalEstablishmentRequest,
  FiscalServiceStatus,
  TaxRegime,
} from '../../models/fiscal-establishment';

@Component({
  standalone: true,
  selector: 'app-fiscal-configuration',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './fiscal-configuration-component.html',
  styleUrl: './fiscal-configuration-component.scss',
})
export class FiscalConfigurationComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly companyService = inject(CompanyService);
  private readonly establishmentService = inject(FiscalEstablishmentService);
  private readonly notification = inject(NotificationService);

  readonly company = signal<Company | null>(null);
  readonly companyLoadFailed = signal(false);
  readonly establishments = signal<FiscalEstablishment[]>([]);
  readonly selected = signal<FiscalEstablishment | null>(null);
  readonly statusMessage = signal<string | null>(null);
  readonly statusCode = signal<string | null>(null);
  readonly isLoading = signal(true);

  isSavingCompany = false;
  isSavingFiscal = false;
  isCheckingStatus = false;
  certificateFile: File | null = null;

  readonly companyForm = this.fb.group({
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

  readonly fiscalForm = this.fb.group({
    municipalityIbgeCode: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/^\d{7}$/),
    ]),
    taxRegime: this.fb.nonNullable.control<TaxRegime>('SIMPLES_NACIONAL', [
      Validators.required,
    ]),
    environment: this.fb.nonNullable.control<FiscalEnvironment>('HOMOLOGACAO', [
      Validators.required,
    ]),
    series: this.fb.nonNullable.control(1, [
      Validators.required,
      Validators.min(1),
      Validators.max(999),
    ]),
    nextNumber: this.fb.nonNullable.control(1, [
      Validators.required,
      Validators.min(1),
    ]),
    cscId: this.fb.control<number | null>(null, [Validators.min(1)]),
    csc: this.fb.nonNullable.control(''),
    certificatePassword: this.fb.nonNullable.control(''),
  });

  ngOnInit(): void {
    this.loadConfiguration();
  }

  private loadConfiguration(): void {
    this.isLoading.set(true);
    this.companyLoadFailed.set(false);
    this.company.set(null);
    this.establishments.set([]);
    this.selected.set(null);
    this.resetCompanyForm();
    this.resetFiscalForm();

    this.companyService
      .find()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: company => {
          this.company.set(company);

          if (!company) {
            this.companyLoadFailed.set(false);
            this.resetCompanyForm();
            this.resetFiscalForm();
            return;
          }

          this.patchCompanyForm(company);
          this.loadEstablishments();
        },
        error: error => {
          this.company.set(null);
          this.establishments.set([]);
          this.selected.set(null);
          this.companyLoadFailed.set(true);
          this.notification.error(
            error?.error?.message ??
              'Não foi possível carregar a empresa proprietária.',
          );
        },
      });
  }

  saveCompany(): void {
    if (this.companyForm.invalid) {
      this.companyForm.markAllAsTouched();
      this.notification.show(
        'Preencha a razão social e um CNPJ válido.',
        'warning',
      );
      return;
    }

    const creatingCompany = !this.company();
    const value = this.companyForm.getRawValue();
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
        inscricaoEstadual: String(value.inscricaoEstadual ?? '').replace(/\D/g, '') || null,
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

    this.isSavingCompany = true;
    this.companyService.save(request).subscribe({
      next: company => {
        this.isSavingCompany = false;
        this.company.set(company);
        this.companyLoadFailed.set(false);
        this.patchCompanyForm(company);
        this.notification.success(
          creatingCompany
            ? 'Empresa proprietária cadastrada com sucesso.'
            : 'Dados da empresa atualizados.',
        );
        this.loadEstablishments();
      },
      error: error => {
        this.isSavingCompany = false;
        this.notification.error(
          error?.error?.message ??
            'Não foi possível salvar os dados da empresa.',
        );
      },
    });
  }

  private loadEstablishments(): void {
    if (!this.company()) {
      this.establishments.set([]);
      this.selected.set(null);
      this.resetFiscalForm();
      return;
    }

    this.establishmentService.findAll().subscribe({
      next: items => {
        this.establishments.set(items);

        if (items.length === 0) {
          this.resetFiscalForm();
          return;
        }

        const currentId = this.selected()?.id;
        const item = items.find(x => x.id === currentId) ?? items[0];
        this.select(item);
      },
      error: error => {
        this.establishments.set([]);
        this.selected.set(null);
        this.resetFiscalForm();
        this.notification.error(
          error?.error?.message ??
            'Não foi possível carregar a configuração fiscal.',
        );
      },
    });
  }

  select(establishment: FiscalEstablishment): void {
    this.selected.set(establishment);
    this.certificateFile = null;
    this.statusMessage.set(null);
    this.statusCode.set(null);
    this.fiscalForm.reset({
      municipalityIbgeCode: establishment.municipalityIbgeCode,
      taxRegime: establishment.taxRegime,
      environment: establishment.environment,
      series: establishment.series,
      nextNumber: establishment.nextNumber,
      cscId: establishment.cscId ?? null,
      csc: '',
      certificatePassword: '',
    });
  }

  private resetFiscalForm(): void {
    this.selected.set(null);
    this.certificateFile = null;
    this.statusMessage.set(null);
    this.statusCode.set(null);
    this.fiscalForm.reset({
      municipalityIbgeCode: '',
      taxRegime: 'SIMPLES_NACIONAL',
      environment: 'HOMOLOGACAO',
      series: 1,
      nextNumber: 1,
      cscId: null,
      csc: '',
      certificatePassword: '',
    });
  }

  private patchCompanyForm(company: Company): void {
    const email = company.contacts.find(x => x.type === 'EMAIL')?.value ?? '';
    const phone =
      company.contacts.find(x => x.principal && x.type !== 'EMAIL')?.value ??
      company.contacts.find(x => x.type !== 'EMAIL')?.value ??
      '';
    const address =
      company.addresses.find(x => x.principal) ?? company.addresses[0];

    this.companyForm.reset({
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

  private resetCompanyForm(): void {
    this.companyForm.reset({
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

  onCertificateSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.certificateFile = input.files?.[0] ?? null;
  }

  saveFiscal(): void {
    if (!this.company()) {
      this.notification.show(
        'Cadastre a empresa proprietária antes de salvar a configuração fiscal.',
        'warning',
      );
      return;
    }

    if (this.fiscalForm.invalid) {
      this.fiscalForm.markAllAsTouched();
      this.notification.show(
        'Preencha os campos obrigatórios da configuração fiscal.',
        'warning',
      );
      return;
    }

    const value = this.fiscalForm.getRawValue();
    const request: FiscalEstablishmentRequest = {
      municipalityIbgeCode: String(value.municipalityIbgeCode ?? '').replace(/\D/g, ''),
      taxRegime: value.taxRegime,
      environment: value.environment,
      series: Number(value.series),
      nextNumber: Number(value.nextNumber),
      cscId: value.cscId == null ? null : Number(value.cscId),
      csc: String(value.csc ?? '').trim() || null,
      certificatePassword: String(value.certificatePassword ?? '').trim() || null,
    };

    this.isSavingFiscal = true;
    const selectedId = this.selected()?.id;
    const request$ = selectedId
      ? this.establishmentService.update(selectedId, request)
      : this.establishmentService.create(request);

    request$.subscribe({
      next: establishment => this.finishFiscalSave(establishment, request),
      error: error => {
        this.isSavingFiscal = false;
        this.notification.error(
          error?.error?.message ??
            'Não foi possível salvar a configuração fiscal.',
        );
      },
    });
  }

  private finishFiscalSave(
    establishment: FiscalEstablishment,
    request: FiscalEstablishmentRequest,
  ): void {
    const file = this.certificateFile;

    if (!file) {
      this.isSavingFiscal = false;
      this.notification.success('Configuração fiscal salva.');
      this.loadEstablishmentsAndSelect(establishment.id!);
      return;
    }

    const password = request.certificatePassword ?? '';
    if (!password) {
      this.isSavingFiscal = false;
      this.notification.error(
        'Informe a senha do certificado A1 para enviar um novo certificado.',
      );
      return;
    }

    this.establishmentService
      .uploadCertificate(establishment.id!, file, password)
      .subscribe({
        next: () => {
          this.isSavingFiscal = false;
          this.notification.success(
            'Configuração fiscal e certificado A1 salvos.',
          );
          this.loadEstablishmentsAndSelect(establishment.id!);
        },
        error: error => {
          this.isSavingFiscal = false;
          this.notification.error(
            error?.error?.message ??
              'Não foi possível armazenar o certificado A1.',
          );
        },
      });
  }

  private loadEstablishmentsAndSelect(id: number): void {
    this.establishmentService.findAll().subscribe({
      next: items => {
        this.establishments.set(items);
        const item = items.find(x => x.id === id);
        if (item) {
          this.select(item);
        }
      },
      error: () => {
        this.notification.error(
          'A configuração foi salva, mas não foi possível atualizar a tela.',
        );
      },
    });
  }

  checkSefaz(): void {
    const id = this.selected()?.id;
    if (!id) return;

    this.isCheckingStatus = true;
    this.establishmentService.checkStatus(id).subscribe({
      next: (result: FiscalServiceStatus) => {
        this.isCheckingStatus = false;
        this.statusCode.set(result.statusCode ?? null);
        this.statusMessage.set(
          result.reason ?? 'Resposta recebida da SEFAZ/MG.',
        );
      },
      error: error => {
        this.isCheckingStatus = false;
        this.statusCode.set(null);
        this.statusMessage.set(
          error?.error?.message ??
            'Falha na consulta do serviço da SEFAZ/MG.',
        );
      },
    });
  }
}
