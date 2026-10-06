import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { FiscalEstablishmentService } from '../../../core/services/fiscal-establishment-service';
import { NotificationService } from '../../../core/services/notification-service';
import { FiscalEnvironment, FiscalEstablishment, FiscalEstablishmentRequest, TaxRegime } from '../../models/fiscal-establishment';
import { PersonService } from '../../../core/services/person-service';
import { PersonOption } from '../../models/person-option';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  selector: 'app-fiscal-settings-component',
  styleUrl: './fiscal-settings-component.scss',
  templateUrl: './fiscal-settings-component.html',
})
export class FiscalSettingsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly establishmentService = inject(FiscalEstablishmentService);
  private readonly personService = inject(PersonService);
  private readonly notification = inject(NotificationService);

  establishments = signal<FiscalEstablishment[]>([]);
  legalEntities = signal<PersonOption[]>([]);
  selected = signal<FiscalEstablishment | null>(null);
  isSaving = false;
  isCheckingStatus = false;
  statusMessage = signal<string | null>(null);
  statusCode = signal<string | null>(null);
  certificateFile: File | null = null;
  personSearch = '';

  form = this.fb.group({
    personId: [null as number | null, Validators.required],
    municipalityIbgeCode: ['', [Validators.required, Validators.pattern(/^\d{7}$/)]],
    taxRegime: ['SIMPLES_NACIONAL' as TaxRegime, Validators.required],
    environment: ['HOMOLOGACAO' as FiscalEnvironment, Validators.required],
    series: [1, [Validators.required, Validators.min(1), Validators.max(999)]],
    nextNumber: [1, [Validators.required, Validators.min(1)]],
    cscId: [null as number | null, [Validators.min(1)]],
    csc: [''],
    certificatePassword: [''],
  });

  ngOnInit(): void {
    this.loadEstablishments();
    this.loadLegalEntities();
  }

  loadEstablishments(): void {
    this.establishmentService.findAll().subscribe({
      next: items => {
        this.establishments.set(items);
        if (items.length > 0 && !this.selected()) this.select(items[0]);
        if (items.length === 0) this.resetForCreate();
      },
      error: () => this.notification.error('Não foi possível carregar a configuração fiscal.'),
    });
  }

  loadLegalEntities(): void {
    this.personService.findLegalEntities(this.personSearch, 0, 30).subscribe({
      next: page => this.legalEntities.set(page.content ?? []),
      error: () => this.legalEntities.set([]),
    });
  }

  searchLegalEntities(): void { this.loadLegalEntities(); }

  select(establishment: FiscalEstablishment): void {
    this.selected.set(establishment);
    this.certificateFile = null;
    this.statusMessage.set(null);
    this.form.reset({
      personId: establishment.personId,
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

  resetForCreate(): void {
    this.selected.set(null);
    this.certificateFile = null;
    this.statusMessage.set(null);
    this.statusCode.set(null);
    this.form.reset({
      personId: null,
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

  onCertificateSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.certificateFile = input.files?.[0] ?? null;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show('Preencha os campos obrigatórios da configuração fiscal.', 'warning');
      return;
    }

    const value = this.form.getRawValue();
    const request: FiscalEstablishmentRequest = {
      personId: Number(value.personId),
      municipalityIbgeCode: String(value.municipalityIbgeCode ?? '').replace(/\D/g, ''),
      taxRegime: value.taxRegime as TaxRegime,
      environment: value.environment as FiscalEnvironment,
      series: Number(value.series),
      nextNumber: Number(value.nextNumber),
      cscId: value.cscId == null ? null : Number(value.cscId),
      csc: value.csc?.trim() || null,
      certificatePassword: value.certificatePassword?.trim() || null,
    };

    this.isSaving = true;
    const request$ = this.selected()?.id
      ? this.establishmentService.update(this.selected()!.id!, request)
      : this.establishmentService.create(request);

    request$.subscribe({
      next: establishment => this.finishSave(establishment, request),
      error: error => {
        this.isSaving = false;
        this.notification.error(error?.error?.message ?? 'Não foi possível salvar a configuração fiscal.');
      },
    });
  }

  private finishSave(establishment: FiscalEstablishment, request: FiscalEstablishmentRequest): void {
    const file = this.certificateFile;
    if (!file) {
      this.isSaving = false;
      this.notification.success('Configuração fiscal salva.');
      this.loadEstablishmentsAndSelect(establishment.id!);
      return;
    }

    const password = request.certificatePassword ?? '';
    if (!password) {
      this.isSaving = false;
      this.notification.error('Informe a senha do certificado A1 para enviar um novo certificado.');
      return;
    }

    this.establishmentService.uploadCertificate(establishment.id!, file, password).subscribe({
      next: () => {
        this.isSaving = false;
        this.notification.success('Configuração fiscal e certificado A1 salvos.');
        this.loadEstablishmentsAndSelect(establishment.id!);
      },
      error: error => {
        this.isSaving = false;
        this.notification.error(error?.error?.message ?? 'Não foi possível armazenar o certificado A1.');
      },
    });
  }

  private loadEstablishmentsAndSelect(id: number): void {
    this.establishmentService.findAll().subscribe({
      next: items => {
        this.establishments.set(items);
        const item = items.find(x => x.id === id);
        if (item) this.select(item);
      },
    });
  }

  checkSefaz(): void {
    const id = this.selected()?.id;
    if (!id) return;
    this.isCheckingStatus = true;
    this.establishmentService.checkStatus(id).subscribe({
      next: result => {
        this.isCheckingStatus = false;
        this.statusCode.set(result.statusCode ?? null);
        this.statusMessage.set(result.reason ?? 'Resposta recebida da SEFAZ/MG.');
      },
      error: error => {
        this.isCheckingStatus = false;
        this.statusCode.set(null);
        this.statusMessage.set(error?.error?.message ?? 'Falha na consulta do serviço da SEFAZ/MG.');
      },
    });
  }

  private selectedName(): string {
    const current = this.form.controls.personId.value;
    const person = this.legalEntities().find(item => item.id === current);
    return person ? (person.tradeName || person.name) : 'Selecione a pessoa jurídica';
  }

  displaySelectedName(): string { return this.selectedName(); }
}
