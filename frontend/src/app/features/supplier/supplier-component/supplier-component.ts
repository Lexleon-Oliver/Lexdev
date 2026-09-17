import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { Supplier } from '../../models/supplier';
import { cpfCnpjValidator } from '../../../core/validators/cpf-cnpj.validator';
import { TipoConta } from '../../models/tipo-conta';
import { SupplierContact } from '../../models/supplier-contact';
import { SupplierService } from '../../../core/services/supplier-service';
import { CepService } from '../../../core/services/cep-service';

@Component({
  imports: [FormsModule, ReactiveFormsModule],
  selector: 'app-supplier-component',
  styleUrl: './supplier-component.scss',
  templateUrl: './supplier-component.html',
})
export class SupplierComponent implements OnInit {
  private readonly supplierService = inject(SupplierService);
  private readonly cepService = inject(CepService);
  private readonly fb = inject(FormBuilder);
  private readonly notification = inject(NotificationService);

  // Estado
  suppliers = signal<Supplier[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');
  isSearchingCep = signal(false);

  // Paginação
  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  filteredSuppliers = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    const currentSuppliers = this.suppliers();

    if (!Array.isArray(currentSuppliers)) return [];
    if (!term) return currentSuppliers;

    return currentSuppliers.filter(s =>
      s.name?.toLowerCase().includes(term) ||
      s.email?.toLowerCase().includes(term) ||
      s.phone?.includes(term) ||
      s.cpfCnpj?.includes(term) ||
      s.categoria?.toLowerCase().includes(term)
    );
  });

  // Modais e UI
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingSupplier = signal<Supplier | null>(null);
  deletingSupplier = signal<Supplier | null>(null);
  isSaving = signal(false);
  activeTab = signal<'identification' | 'address' | 'commercial' | 'bank' | 'contacts'>('identification');

  // Listas auxiliares
  readonly ufList = [
    'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA',
    'PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
  ];

  readonly categoriaList = [
    'Tecnologia', 'Serviços', 'Matéria-prima', 'Logística',
    'Marketing', 'Consultoria', 'Manutenção', 'Outros'
  ];

  readonly condicaoPagamentoList = [
    'À Vista', '15 DIAS', '30 DIAS', '45 DIAS',
    '60 DIAS', '90 DIAS', 'Parcelado'
  ];

  form: FormGroup = this.fb.group({
    tipoPessoa: ['PF', Validators.required],
    name: ['', Validators.required],
    nomeFantasia: [''],
    cpfCnpj: ['', [Validators.required, cpfCnpjValidator()]],
    rgIe: [''],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.required],
    ativo: [true],
    cep: [''],
    logradouro: [''],
    numero: [''],
    complemento: [''],
    bairro: [''],
    cidade: [''],
    uf: [''],
    categoria: [''],
    condicaoPagamento: [''],
    prazoEntrega: [null as number | null],
    valorMinimoPedido: [null as number | null],
    observacoesComerciais: [''],
    banco: [''],
    agencia: [''],
    conta: [''],
    tipoConta: ['' as TipoConta | ''],
    chavePix: [''],
    contatos: this.fb.array([])
  });

  get contatos(): FormArray {
    return this.form.get('contatos') as FormArray;
  }

  ngOnInit(): void {
    this.loadSuppliers();
  }

  loadSuppliers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.supplierService.findAll(this.currentPage(), this.pageSize()).subscribe({
      next: (page) => {
        this.suppliers.set(page.content || []);
        this.totalElements.set(page.totalElements ?? 0);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Erro ao carregar fornecedores.');
        this.isLoading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.editingSupplier.set(null);
    this.contatos.clear();
    this.form.reset({
      tipoPessoa: 'PF',
      tipoConta: ''
    });
    this.activeTab.set('identification');
    this.showFormModal.set(true);
  }

  openEditModal(supplier: Supplier): void {
    this.editingSupplier.set(supplier);
    this.contatos.clear();
    this.isLoading.set(true);

    this.supplierService.findById(supplier.id!).subscribe({
      next: (fullSupplier: any) => {
        // Preenche os contatos (mapeando setor do backend para departamento do form)
        (fullSupplier.contatos || []).forEach((c: any) => {
          this.contatos.push(this.buildContatoGroup(c));
        });

        // Formata o valor mínimo para exibição em moeda
        let valorMin = fullSupplier.valorMinimoPedido;
        if (valorMin !== null && valorMin !== undefined && typeof valorMin === 'number') {
          valorMin = Number(valorMin).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
        }

        this.form.patchValue({
          tipoPessoa: fullSupplier.tipoPessoa,
          name: fullSupplier.name ?? '',
          nomeFantasia: fullSupplier.nomeFantasia ?? '',
          cpfCnpj: fullSupplier.cpfCnpj ?? '',
          rgIe: fullSupplier.rgIe ?? '',
          email: fullSupplier.email ?? '',
          phone: fullSupplier.phone ?? '',
          ativo: fullSupplier.active ?? true,

          cep: fullSupplier.cep ?? '',
          logradouro: fullSupplier.logradouro ?? '',
          numero: fullSupplier.numero ?? '',
          complemento: fullSupplier.complemento ?? '',
          bairro: fullSupplier.bairro ?? '',
          cidade: fullSupplier.cidade ?? '',
          uf: fullSupplier.uf ?? '',

          // Comercial
          categoria: fullSupplier.categoria ?? '',
          condicaoPagamento: fullSupplier.condicaoPagamentoPadrao ?? '',
          prazoEntrega: fullSupplier.prazoEntregaDias ?? null,
          valorMinimoPedido: valorMin ?? null,
          observacoesComerciais: fullSupplier.observacoesComerciais ?? '',

          // Bancário (Lendo de dentro de bankDetails)
          banco: fullSupplier.bankDetails?.banco ?? '',
          agencia: fullSupplier.bankDetails?.agencia ?? '',
          conta: fullSupplier.bankDetails?.conta ?? '',
          tipoConta: fullSupplier.bankDetails?.tipoConta ?? '',
          chavePix: fullSupplier.bankDetails?.chavePix ?? ''
        });

        this.isLoading.set(false);
        this.activeTab.set('identification');
        this.showFormModal.set(true);
      },
      error: () => {
        this.isLoading.set(false);
        this.notification.error('Erro ao carregar os dados completos do fornecedor.');
      }
    });
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
    const payload = this.normalizePayload(this.form.value);
    const editing = this.editingSupplier();

    if (editing?.id) {
      this.supplierService.update(editing.id, payload).subscribe({
        next: (updated) => {
          this.suppliers.update(list => list.map(s => s.id === editing.id ? updated : s));
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
          this.notification.error('Erro ao criar fornecedor.');
          this.isSaving.set(false);
        }
      });
    }
  }

  confirmDelete(supplier: Supplier): void {
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

  addContato(): void {
    this.contatos.push(this.buildContatoGroup());
  }

  removeContato(index: number): void {
    this.contatos.removeAt(index);
  }

  // Helpers
  toggleAtivo(): void {
    const ctrl = this.form.get('ativo');
    ctrl?.setValue(!ctrl.value);
  }

  private normalizePayload(raw: any): any {
    // Converte o valor monetário formatado (ex: "R$ 1.500,00") para número puro para o BigDecimal
    let valorMin = raw.valorMinimoPedido;
    if (typeof valorMin === 'string') {
      const cleanVal = valorMin.replace(/[^\d,]/g, '').replace(',', '.');
      valorMin = cleanVal ? Number(cleanVal) : null;
    }

    return {
      tipoPessoa: raw.tipoPessoa,
      name: raw.name,
      nomeFantasia: raw.tipoPessoa === 'PJ' ? raw.nomeFantasia : '',
      cpfCnpj: raw.cpfCnpj,
      rgIe: raw.rgIe,
      email: raw.email,
      phone: raw.phone,
      cep: raw.cep,
      logradouro: raw.logradouro,
      numero: raw.numero,
      complemento: raw.complemento,
      bairro: raw.bairro,
      cidade: raw.cidade,
      uf: raw.uf,

      // --- Mapeamento Comercial correto para o DTO ---
      condicaoPagamentoPadrao: raw.condicaoPagamento || null,
      prazoEntregaDias: raw.prazoEntrega !== null && raw.prazoEntrega !== '' ? Number(raw.prazoEntrega) : null,
      valorMinimoPedido: valorMin,
      categoria: raw.categoria || null,
      observacoesComerciais: raw.observacoesComerciais || '',

      // --- Mapeamento Bancário como objeto aninhado (BankDetailsDto) ---
      bankDetails: {
        banco: raw.banco || null,
        agencia: raw.agencia || null,
        conta: raw.conta || null,
        tipoConta: raw.tipoConta || null,
        chavePix: raw.chavePix || null
      },

      // --- Mapeamento de Contatos (convertendo departamento para setor) ---
      contatos: (raw.contatos || [])
        .filter((c: any) => c.nome && c.nome.trim().length > 0)
        .map((c: any) => ({
          nome: c.nome,
          cargo: c.cargo || '',
          email: c.email || '',
          telefone: c.telefone || '',
          setor: c.departamento || c.setor || '' // Mapeia 'departamento' do form para 'setor' do DTO
        })),

      active: raw.ativo ?? true
    };
  }

  // Ajuste também a leitura do contato ao abrir a edição para mapear setor -> departamento
  private buildContatoGroup(c: any = { nome: '', cargo: '', email: '', telefone: '', setor: '' }): FormGroup {
    return this.fb.group({
      nome: [c.nome ?? '', Validators.required],
      cargo: [c.cargo ?? ''],
      email: [c.email ?? '', Validators.email],
      telefone: [c.telefone ?? ''],
      departamento: [c.setor ?? c.departamento ?? ''] // Lê 'setor' do backend e coloca no input 'departamento'
    });
  }

  // Máscaras e Formatações
  formatCpfCnpj(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 14) value = value.substring(0, 14);

    if (value.length <= 11) {
      value = value
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
    } else {
      value = value
        .replace(/^(\d{2})(\d)/, '$1.$2')
        .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d)/, '.$1/$2')
        .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
    }
    this.form.get('cpfCnpj')?.setValue(value, { emitEvent: false });
  }

  formatPhone(event: Event): void {
    this.formatPhoneControl(this.form.get('phone'), event);
  }

  formatPhoneControl(ctrl: AbstractControl | null, event: Event): void {
    if (!ctrl) return;
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 11) value = value.substring(0, 11);

    if (value.length <= 10) {
      value = value.replace(/^(\d{2})(\d)/, '($1) $2').replace(/(\d{4})(\d)/, '$1-$2');
    } else {
      value = value.replace(/^(\d{2})(\d)/, '($1) $2').replace(/(\d{5})(\d)/, '$1-$2');
    }
    ctrl.setValue(value, { emitEvent: false });
  }

  formatCep(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 8) value = value.substring(0, 8);
    value = value.replace(/^(\d{5})(\d)/, '$1-$2');
    this.form.get('cep')?.setValue(value, { emitEvent: false });
  }

  formatCurrency(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (!value) {
      this.form.get('valorMinimoPedido')?.setValue(null, { emitEvent: false });
      return;
    }
    const numberValue = Number(value) / 100;
    const formatted = numberValue.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    this.form.get('valorMinimoPedido')?.setValue(formatted, { emitEvent: false });
  }

  buscarCep(): void {
    const cep = this.form.get('cep')?.value;
    if (!cep || cep.replace(/\D/g, '').length !== 8) return;

    this.isSearchingCep.set(true); // Opcional, como mantivemos na refatoração anterior

    this.cepService.buscarCep(cep).subscribe({
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
}
