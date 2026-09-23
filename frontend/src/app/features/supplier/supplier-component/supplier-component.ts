import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { SupplierResponseDto } from '../../models/supplier-response-dto';
import { cpfCnpjValidator } from '../../../core/validators/cpf-cnpj.validator';
import { SupplierContactDto } from '../../models/supplier-contact-dto';
import { SupplierService } from '../../../core/services/supplier-service';
import { PersonAddressResponseDto } from '../../models/person-address-response-dto';
import { PersonContactResponseDto } from '../../models/person-contact-response-dto';
import { SupplierRequestDto } from '../../models/supplier-request-dto';

@Component({
  imports: [FormsModule, ReactiveFormsModule],
  selector: 'app-supplier-component',
  styleUrl: './supplier-component.scss',
  templateUrl: './supplier-component.html',
})
export class SupplierComponent implements OnInit {
  private readonly supplierService = inject(SupplierService);
  private readonly fb = inject(FormBuilder);
  private readonly notification = inject(NotificationService);

  /* ===================== Estado ===================== */
  suppliers = signal<SupplierResponseDto[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');
  isSearchingCep = signal(false);

  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  /* ===================== Filtro ===================== */
  filteredSuppliers = computed(() => {

    const term =
      this.searchTerm().trim();

    const normalizedTerm =
      this.normalizeSearchValue(term);

    const list =
      this.suppliers();

    if (!Array.isArray(list)) {
      return [];
    }

    if (!term) {
      return list;
    }

    return list.filter((supplier) => {

      const name =
        this.normalizeSearchValue(
          supplier.person?.name ?? ''
        );

      const doc =
        this.normalizeSearchValue(
          supplier.person?.cpfCnpj ?? ''
        );

      const email =
        this.normalizeSearchValue(
          this.getPrimaryContactValue(
            supplier,
            'EMAIL'
          )
        );

      const phone =
        this.normalizeSearchValue(
          this.getPrimaryContactValue(
            supplier,
            'TELEFONE'
          )
        );

      const categoria =
        this.normalizeSearchValue(
          supplier.categoria ?? ''
        );

      return (
        name.includes(normalizedTerm) ||
        doc.includes(normalizedTerm) ||
        email.includes(normalizedTerm) ||
        phone.includes(normalizedTerm) ||
        categoria.includes(normalizedTerm)
      );
    });
  });

  /* ===================== UI ===================== */
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingSupplier = signal<SupplierResponseDto | null>(null);
  deletingSupplier = signal<SupplierResponseDto | null>(null);
  isSaving = signal(false);

  activeTab = signal<'identification' | 'address' | 'commercial' | 'bank' | 'contacts'>('identification');

  /* ===================== Listas auxiliares ===================== */
  readonly ufList = [
    'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA',
    'PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
  ];

  readonly categoriaList = [
    'Tecnologia', 'Serviços', 'Matéria-prima', 'Logística',
    'Marketing', 'Consultoria', 'Manutenção', 'Outros'
  ];

  readonly condicaoPagamentoList = [
    'À Vista', '15 DIAS', '30 DIAS', '45 DIAS', '60 DIAS', '90 DIAS', 'Parcelado'
  ];

  /* ===================== Form ===================== */
  form: FormGroup = this.fb.group({
    // Identificação
    tipoPessoa: ['PF', Validators.required],       // ⬅ PF/PJ (novo padrão)
    name: ['', Validators.required],
    nomeFantasia: [''],
    cpfCnpj: ['', [Validators.required, cpfCnpjValidator()]],
    rgIe: [''],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.required],
    ativo: [true],

    // Endereço
    cep: [''],
    logradouro: [''],
    numero: [''],
    complemento: [''],
    bairro: [''],
    cidade: [''],
    uf: [''],

    // Comercial
    categoria: [''],
    condicaoPagamento: [''],
    prazoEntrega: [null as number | null],
    valorMinimoPedido: [null as number | null],
    observacoesComerciais: [''],

    // Bancário
    banco: [''],
    agencia: [''],
    conta: [''],
    tipoConta: [''],
    chavePix: [''],

    // Contatos
    contatos: this.fb.array([])
  });

  get contatos(): FormArray {
    return this.form.get('contatos') as FormArray;
  }

  /* ===================== Lifecycle ===================== */
  ngOnInit(): void {
    this.loadSuppliers();
  }

  /* ===================== Helpers de apresentação ===================== */
/** Extrai o valor do contato principal (ou primeiro) por tipo. */
  getPrimaryContactValue(supplier: SupplierResponseDto, type: string): string {
    const contacts = supplier.contacts ?? [];

    // Tipagem explícita no parâmetro 'c' para evitar o erro 7006
    const principal = contacts.find((c: PersonContactResponseDto) => c.type === type && c.principal);
    return (principal ?? contacts.find((c: PersonContactResponseDto) => c.type === type))?.value ?? '—';
  }

  /** Retorna o endereço principal (ou o COMERCIAL, ou o primeiro). */
  getPrimaryAddress(supplier: SupplierResponseDto): PersonAddressResponseDto | null {
    const addresses = supplier.addresses ?? [];
    if (!addresses.length) return null;
    return (
      addresses.find(a => a.principal) ??
      addresses.find(a => a.type === 'COMERCIAL') ??
      addresses[0]
    );
  }

  /* ===================== CRUD ===================== */
  loadSuppliers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.supplierService.findAll(this.currentPage(), this.pageSize()).subscribe({
      next: (page) => {
        this.suppliers.set(page.content ?? []);
        this.totalElements.set(page.totalElements ?? 0);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.editingSupplier.set(null);
    this.contatos.clear();
    this.form.reset({
      tipoPessoa: 'PF',
      ativo: true,
      tipoConta: ''
    });
    this.activeTab.set('identification');
    this.showFormModal.set(true);
  }

  openEditModal(supplier: SupplierResponseDto): void {
    this.editingSupplier.set(supplier);
    this.contatos.clear();

    (supplier.contatos ?? []).forEach(c => {
      this.contatos.push(this.buildContatoGroup(c));
    });

    const addr = this.getPrimaryAddress(supplier);
    const isPJ = supplier.person?.tipoPessoa === 'PJ';

    this.form.patchValue({
      tipoPessoa: supplier.person?.tipoPessoa ?? 'PF',
      name: supplier.person?.name ?? '',
      nomeFantasia: supplier.legalEntity?.nomeFantasia ?? '',
      cpfCnpj: this.applyCpfCnpjMask(
        supplier.person?.cpfCnpj,
        supplier.person?.tipoPessoa
      ),
      rgIe: isPJ
        ? (supplier.legalEntity?.inscricaoEstadual ?? '')
        : (supplier.individual?.rg ?? ''),
      email: this.getPrimaryContactValue(supplier, 'EMAIL'),
      phone: this.applyPhoneMask(
        this.getPrimaryContactValue(
          supplier,
          'TELEFONE'
        )
      ),
      ativo: supplier.active ?? true,

      cep: this.applyCepMask(
        addr?.cep
      ),
      logradouro: addr?.logradouro ?? '',
      numero: addr?.numero ?? '',
      complemento: addr?.complemento ?? '',
      bairro: addr?.bairro ?? '',
      cidade: addr?.cidade ?? '',
      uf: addr?.uf ?? '',

      categoria: supplier.categoria ?? '',
      condicaoPagamento: supplier.condicaoPagamentoPadrao ?? '',
      prazoEntrega: supplier.prazoEntregaDias ?? null,
      valorMinimoPedido:
        this.formatCurrencyValue(
          supplier.valorMinimoPedido
        ),
      observacoesComerciais: supplier.observacoesComerciais ?? '',

      banco: supplier.bankDetails?.banco ?? '',
      agencia: supplier.bankDetails?.agencia ?? '',
      conta: supplier.bankDetails?.conta ?? '',
      tipoConta: supplier.bankDetails?.tipoConta ?? '',
      chavePix: supplier.bankDetails?.chavePix ?? ''
    });

    this.activeTab.set('identification');
    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    this.showFormModal.set(false);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show('Preencha todos os campos obrigatórios.', 'warning');
      return;
    }

    this.isSaving.set(true);
    const payload = this.toRequestDto(this.form.value);
    const editing = this.editingSupplier();

    if (editing?.id) {
      this.supplierService.update(editing.id, payload).subscribe({
        next: (updated) => {
          this.suppliers.update(list =>
            list.map(s => (s.id === editing.id ? updated : s))
          );
          this.notification.success('Fornecedor atualizado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao atualizar fornecedor.');
          this.isSaving.set(false);
        }
      });
    } else {
      this.supplierService.create(payload).subscribe({
        next: (created) => {
          this.suppliers.update(list => [created, ...list]);
          this.notification.success('Fornecedor criado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.isSaving.set(false);
        }
      });
    }
  }

  confirmDelete(supplier: SupplierResponseDto): void {
    this.deletingSupplier.set(supplier);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deletingSupplier.set(null);
  }

  deleteSupplier(): void {
    const supplier = this.deletingSupplier();
    if (!supplier?.id) return;

    this.supplierService.delete(supplier.id).subscribe({
      next: () => {
        this.suppliers.update(list => list.filter(s => s.id !== supplier.id));
        this.notification.success('Fornecedor excluído com sucesso!');
        this.closeDeleteModal();
      },
      error: () => this.notification.error('Erro ao excluir fornecedor.')
    });
  }

  /* ===================== Mapeamento form → DTO ===================== */
  private toRequestDto(raw: any): SupplierRequestDto {
    const isPJ = raw.tipoPessoa === 'PJ';

    const contacts: Omit<PersonContactResponseDto, 'id'>[] = [
      {
        type: 'EMAIL',
        value: raw.email,
        principal: true,
        description: undefined
      } as any,
      {
        type: 'TELEFONE',
        value: raw.phone
          ? raw.phone.replace(/\D/g, '')
          : '',
        principal: true,
        description: undefined
      } as any
    ];

    const addresses: Omit<PersonAddressResponseDto, 'id'>[] = [
      {
        type: 'COMERCIAL',
        cep: raw.cep
          ? raw.cep.replace(/\D/g, '')
          : '',
        logradouro: raw.logradouro,
        numero: raw.numero,
        complemento: raw.complemento,
        bairro: raw.bairro,
        cidade: raw.cidade,
        uf: raw.uf,
        principal: true
      } as any
    ];

    return {
      person: {
        tipoPessoa: raw.tipoPessoa,
        name: raw.name,
        cpfCnpj: raw.cpfCnpj
          ? raw.cpfCnpj.replace(/\D/g, '')
          : '',
        individualPerson: !isPJ ? { rg: raw.rgIe } : undefined,
        legalEntity: isPJ
          ? {
              nomeFantasia: raw.nomeFantasia,
              inscricaoEstadual: raw.rgIe
            }
          : undefined,
        contacts,
        addresses
      },
      condicaoPagamentoPadrao: raw.condicaoPagamento,
      prazoEntregaDias: raw.prazoEntrega != null && raw.prazoEntrega !== ''
        ? Number(raw.prazoEntrega)
        : 0,
      valorMinimoPedido: raw.valorMinimoPedido != null && raw.valorMinimoPedido !== ''
        ? this.parseCurrency(raw.valorMinimoPedido)
        : 0,
      categoria: raw.categoria,
      observacoesComerciais: raw.observacoesComerciais,
      bankDetails: {
        banco: raw.banco,
        agencia: raw.agencia,
        conta: raw.conta,
        tipoConta: raw.tipoConta,
        chavePix: raw.chavePix
      },
      contatos: (raw.contatos || [])
        .filter(
          (c: SupplierContactDto) =>
            c.nome?.trim().length > 0
        )
        .map(
          (c: SupplierContactDto) => ({
            ...c,
            telefone: c.telefone
              ? c.telefone.replace(/\D/g, '')
              : c.telefone
          })
        ),
      documentos: [],
      active: !!raw.ativo
    };
  }

  /** Aceita "R$ 1.234,56" ou number e devolve number. */
  private parseCurrency(value: any): number {
    if (typeof value === 'number') return value;
    const digits = String(value).replace(/\D/g, '');
    return digits ? Number(digits) / 100 : 0;
  }

  /* ===================== Contatos (FormArray) ===================== */
  private buildContatoGroup(
    c: Partial<SupplierContactDto> = {}
  ): FormGroup {

    return this.fb.group({

      nome: [
        c.nome ?? '',
        Validators.required
      ],

      cargo: [
        c.cargo ?? ''
      ],

      email: [
        c.email ?? '',
        Validators.email
      ],

      telefone: [
        this.applyPhoneMask(
          c.telefone
        )
      ],

      setor: [
        c.setor ?? ''
      ]

    });
  }

  addContato(): void {
    this.contatos.push(this.buildContatoGroup());
  }

  removeContato(index: number): void {
    this.contatos.removeAt(index);
  }

  /* ===================== Helpers ===================== */
  toggleAtivo(): void {
    const ctrl = this.form.get('ativo');
    ctrl?.setValue(!ctrl.value);
  }

  /* ===================== Máscaras ===================== */
  formatCpfCnpj(event: Event): void {

    const input =
      event.target as HTMLInputElement;

    const tipoPessoa =
      this.form.get('tipoPessoa')?.value;

    const maskedValue =
      this.applyCpfCnpjMask(
        input.value,
        tipoPessoa
      );

    this.form
      .get('cpfCnpj')
      ?.setValue(
        maskedValue,
        {
          emitEvent: false
        }
      );
  }

  formatPhone(event: Event): void {
    this.formatPhoneControl(
      this.form.get('phone'),
      event
    );
  }

  formatPhoneControl(
    ctrl: AbstractControl | null,
    event: Event
  ): void {

    if (!ctrl) {
      return;
    }

    const input =
      event.target as HTMLInputElement;

    const maskedValue =
      this.applyPhoneMask(
        input.value
      );

    ctrl.setValue(
      maskedValue,
      {
        emitEvent: false
      }
    );
  }

  formatCep(event: Event): void {

    const input =
      event.target as HTMLInputElement;

    const maskedValue =
      this.applyCepMask(
        input.value
      );

    this.form
      .get('cep')
      ?.setValue(
        maskedValue,
        {
          emitEvent: false
        }
      );
  }

  private applyCepMask(
    value: string | undefined | null
  ): string {

    if (!value) {
      return '';
    }

    let str =
      value.replace(/\D/g, '');

    if (str.length > 8) {
      str = str.substring(0, 8);
    }

    return str.replace(
      /^(\d{5})(\d)/,
      '$1-$2'
    );
  }

  formatCurrency(event: Event): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '');
    if (!digits) {
      this.form.get('valorMinimoPedido')?.setValue(null, { emitEvent: false });
      input.value = '';
      return;
    }
    const numberValue = Number(digits) / 100;
    const formatted = numberValue.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    });
    this.form.get('valorMinimoPedido')?.setValue(formatted, { emitEvent: false });
  }

  private formatCurrencyValue(
    value: number | null | undefined
  ): string {

    if (value == null) {
      return '';
    }

    return value.toLocaleString(
      'pt-BR',
      {
        style: 'currency',
        currency: 'BRL'
      }
    );
  }

  applyCpfCnpjMask(
    value: string | undefined | null,
    tipoPessoa?: 'PF' | 'PJ' | string
  ): string {

    if (!value) {
      return '';
    }

    let str = value.replace(/\D/g, '');

    const isPJ =
      tipoPessoa === 'PJ'
        ? true
        : tipoPessoa === 'PF'
          ? false
          : str.length > 11;

    if (isPJ) {

      if (str.length > 14) {
        str = str.substring(0, 14);
      }

      return str
        .replace(
          /^(\d{2})(\d)/,
          '$1.$2'
        )
        .replace(
          /^(\d{2})\.(\d{3})(\d)/,
          '$1.$2.$3'
        )
        .replace(
          /\.(\d{3})(\d)/,
          '.$1/$2'
        )
        .replace(
          /(\d{4})(\d{1,2})$/,
          '$1-$2'
        );
    }

    if (str.length > 11) {
      str = str.substring(0, 11);
    }

    return str
      .replace(
        /(\d{3})(\d)/,
        '$1.$2'
      )
      .replace(
        /(\d{3})(\d)/,
        '$1.$2'
      )
      .replace(
        /(\d{3})(\d{1,2})$/,
        '$1-$2'
      );
  }

  applyPhoneMask(
    value: string | undefined | null
  ): string {

    if (!value) {
      return '';
    }

    let str = value.replace(/\D/g, '');

    if (str.length > 11) {
      str = str.substring(0, 11);
    }

    // Telefone fixo
    if (str.length <= 10) {
      return str
        .replace(
          /^(\d{2})(\d)/,
          '($1) $2'
        )
        .replace(
          /(\d{4})(\d)/,
          '$1-$2'
        );
    }

    // Celular
    return str
      .replace(
        /^(\d{2})(\d)/,
        '($1) $2'
      )
      .replace(
        /(\d{5})(\d)/,
        '$1-$2'
      );
  }

  /* ===================== ViaCEP ===================== */
  buscarCep(): void {
    const cep = (this.form.get('cep')?.value || '').replace(/\D/g, '');
    if (cep.length !== 8) return;

    this.isSearchingCep.set(true);
    this.supplierService.getAddressByCep(cep).subscribe({
      next: (data) => {
        this.isSearchingCep.set(false);
        if (!data.erro) {
          this.form.patchValue({
            logradouro: data.logradouro || '',
            bairro: data.bairro || '',
            cidade: data.localidade || '',
            uf: data.uf || ''
          });
        } else {
          this.notification.show('CEP não encontrado.', 'warning');
        }
      },
      error: () => {
        this.isSearchingCep.set(false);
        this.notification.error('Erro ao buscar CEP.');
      }
    });
  }

  private normalizeSearchValue(
    value: string
  ): string {

    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  }
}
