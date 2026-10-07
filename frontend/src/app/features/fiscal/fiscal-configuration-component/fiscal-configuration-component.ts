import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { CompanyService } from '../../../core/services/company-service';
import { FiscalEstablishmentService } from '../../../core/services/fiscal-establishment-service';
import { NotificationService } from '../../../core/services/notification-service';
import { CepService } from '../../../core/services/cep-service';
import { cpfCnpjValidator } from '../../../core/validators/cpf-cnpj.validator';
import { PersonContactResponseDto } from '../../models/person-contact-response-dto';
import { PersonAddressResponseDto } from '../../models/person-address-response-dto';
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
  private readonly cepService = inject(CepService);

  readonly company = signal<Company | null>(null);
  readonly companyLoadFailed = signal(false);
  readonly establishments = signal<FiscalEstablishment[]>([]);
  readonly selected = signal<FiscalEstablishment | null>(null);
  readonly statusMessage = signal<string | null>(null);
  readonly statusCode = signal<string | null>(null);
  readonly isLoading = signal(true);
  readonly companyTab = signal<'identification' | 'contact' | 'address'>('identification');
  readonly searchingCepIndex = signal<number | null>(null);

  isSavingCompany = false;
  isSavingFiscal = false;
  isCheckingStatus = false;
  certificateFile: File | null = null;

  readonly companyForm: FormGroup = this.fb.group({
    // Identificação — empresa proprietária é obrigatoriamente PJ.
    name: ['', Validators.required],
    cpfCnpj: ['', [Validators.required, cpfCnpjValidator()]],
    nomeFantasia: [''],
    inscricaoEstadual: [''],
    // Contatos — mesmo padrão dos módulos de Clientes/Fornecedores.
    contacts: this.fb.array([]),
    // Endereços — mesmo padrão dos módulos de Clientes/Fornecedores.
    addresses: this.fb.array([]),
  });

  get contacts(): FormArray {
    return this.companyForm.get('contacts') as FormArray;
  }

  get addresses(): FormArray {
    return this.companyForm.get('addresses') as FormArray;
  }

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
    this.resetCompanyForm(true);
    this.resetFiscalForm();

    this.companyService
      .find()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: company => {
          this.company.set(company);

          if (!company) {
            this.companyLoadFailed.set(false);
            this.resetCompanyForm(true);
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
        'Preencha todos os campos obrigatórios da empresa.',
        'warning',
      );
      return;
    }

    const creatingCompany = !this.company();
    const value = this.companyForm.getRawValue();

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
      contacts: (value.contacts as PersonContactResponseDto[]).map((contact) => ({
        type: contact.type,
        value:
          contact.type === 'EMAIL'
            ? String(contact.value ?? '').trim()
            : String(contact.value ?? '').replace(/\D/g, ''),
        principal: contact.principal,
        description: contact.description ?? null,
      })),
      addresses: (value.addresses as PersonAddressResponseDto[]).map((address) => ({
        type: address.type,
        cep: String(address.cep ?? '').replace(/\D/g, '') || null,
        logradouro: String(address.logradouro ?? '').trim() || null,
        numero: String(address.numero ?? '').trim() || null,
        complemento: String(address.complemento ?? '').trim() || null,
        bairro: String(address.bairro ?? '').trim() || null,
        cidade: String(address.cidade ?? '').trim() || null,
        uf: String(address.uf ?? '').trim().toUpperCase() || null,
        principal: address.principal,
      })),
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
    this.companyForm.patchValue({
      name: company.person.name ?? '',
      cpfCnpj: this.applyCpfCnpjMask(company.person.cpfCnpj, 'PJ'),
      nomeFantasia: company.legalEntity?.nomeFantasia ?? '',
      inscricaoEstadual: company.legalEntity?.inscricaoEstadual ?? '',
    });

    this.contacts.clear();
    (company.contacts ?? []).forEach(contact => this.contacts.push(this.createContactGroup(contact)));

    this.addresses.clear();
    (company.addresses ?? []).forEach(address => this.addresses.push(this.createAddressGroup(address)));

    this.companyTab.set('identification');
  }

  private resetCompanyForm(addDefaults = false): void {
    this.companyForm.reset({
      name: '',
      cpfCnpj: '',
      nomeFantasia: '',
      inscricaoEstadual: '',
    });

    this.contacts.clear();
    this.addresses.clear();

    if (addDefaults) {
      this.addContact();
      this.addAddress();
    }

    this.companyTab.set('identification');
  }

  private createContactGroup(contact?: PersonContactResponseDto): FormGroup {
    let maskedValue = contact?.value ?? '';
    if (contact && contact.type !== 'EMAIL') {
      maskedValue = this.applyPhoneMask(maskedValue);
    }

    return this.fb.group({
      id: [contact?.id ?? null],
      type: [contact?.type ?? 'EMAIL', Validators.required],
      value: [maskedValue, Validators.required],
      description: [contact?.description ?? ''],
      principal: [contact?.principal ?? false],
    });
  }

  private createAddressGroup(address?: PersonAddressResponseDto): FormGroup {
    return this.fb.group({
      id: [address?.id ?? null],
      type: [address?.type ?? 'COMERCIAL', Validators.required],
      cep: [this.applyCepMask(address?.cep)],
      logradouro: [address?.logradouro ?? ''],
      numero: [address?.numero ?? '', Validators.required],
      complemento: [address?.complemento ?? ''],
      bairro: [address?.bairro ?? ''],
      cidade: [address?.cidade ?? ''],
      uf: [address?.uf ?? ''],
      principal: [address?.principal ?? false],
    });
  }

  addContact(contact?: PersonContactResponseDto): void {
    this.contacts.push(this.createContactGroup(contact));
  }

  removeContact(index: number): void {
    this.contacts.removeAt(index);
  }

  addAddress(address?: PersonAddressResponseDto): void {
    this.addresses.push(this.createAddressGroup(address));
  }

  removeAddress(index: number): void {
    this.addresses.removeAt(index);
  }

  formatCpfCnpj(event: Event): void {
    const input = event.target as HTMLInputElement;
    const maskedValue = this.applyCpfCnpjMask(input.value, 'PJ');
    this.companyForm.get('cpfCnpj')?.setValue(maskedValue, { emitEvent: false });
  }

  onContactValueInput(event: Event, index: number): void {
    const group = this.contacts.at(index);
    const type = group.get('type')?.value;
    const input = event.target as HTMLInputElement;

    if (type === 'EMAIL') {
      group.get('value')?.setValue(input.value, { emitEvent: false });
      return;
    }

    group.get('value')?.setValue(this.applyPhoneMask(input.value), { emitEvent: false });
  }

  onContactTypeChange(index: number): void {
    this.contacts.at(index).get('value')?.setValue('');
  }

  formatCep(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const maskedValue = this.applyCepMask(input.value);
    this.addresses.at(index).get('cep')?.setValue(maskedValue, { emitEvent: false });
  }

  buscarCep(index: number): void {
    const cepCtrl = this.addresses.at(index).get('cep');
    const cep = String(cepCtrl?.value ?? '').replace(/\D/g, '');
    if (cep.length !== 8) {
      return;
    }

    this.searchingCepIndex.set(index);
    this.cepService.buscarCep(cep).subscribe({
      next: data => {
        this.searchingCepIndex.set(null);
        if (!data.erro) {
          this.addresses.at(index).patchValue({
            logradouro: data.logradouro ?? '',
            bairro: data.bairro ?? '',
            cidade: data.localidade ?? '',
            uf: data.uf ?? '',
          });
        } else {
          this.notification.show('CEP não encontrado.', 'warning');
        }
      },
      error: () => {
        this.searchingCepIndex.set(null);
        this.notification.error('Erro ao buscar CEP.');
      },
    });
  }

  private applyCpfCnpjMask(value: string | undefined | null, tipoPessoa: 'PJ' | 'PF' | string = 'PJ'): string {
    if (!value) {
      return '';
    }

    let str = value.replace(/\D/g, '');
    const isPJ = tipoPessoa === 'PJ' || str.length > 11;

    if (isPJ) {
      str = str.substring(0, 14);
      return str
        .replace(/^(\d{2})(\d)/, '$1.$2')
        .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d)/, '.$1/$2')
        .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
    }

    str = str.substring(0, 11);
    return str
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d)/, '$1.$2')
      .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
  }

  private applyPhoneMask(value: string | undefined | null): string {
    if (!value) return '';
    let str = value.replace(/\D/g, '');
    if (str.length > 11) str = str.substring(0, 11);

    if (str.length <= 10) {
      return str
        .replace(/^(\d{2})(\d)/, '($1) $2')
        .replace(/(\d{4})(\d)/, '$1-$2');
    }

    return str
      .replace(/^(\d{2})(\d)/, '($1) $2')
      .replace(/(\d{5})(\d)/, '$1-$2');
  }

  private applyCepMask(value: string | undefined | null): string {
    if (!value) return '';
    let str = value.replace(/\D/g, '');
    if (str.length > 8) str = str.substring(0, 8);
    return str.replace(/^(\d{5})(\d)/, '$1-$2');
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
