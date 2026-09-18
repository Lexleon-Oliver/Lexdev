import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { NotificationService } from '../../../core/services/notification-service';
import { AbstractControl, FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ProductService } from '../../../core/services/product-service';
import { SupplierService } from '../../../core/services/supplier-service';
import { Product } from '../../models/product';
import { Supplier } from '../../models/supplier';
import { ProductSupplier } from '../../models/product-supplier';

@Component({
  imports: [FormsModule,ReactiveFormsModule],
  selector: 'app-product-page',
  styleUrl: './product-page.scss',
  templateUrl: './product-page.html',
})
export class ProductPage implements OnInit {
  private readonly produtoService = inject(ProductService);
  private readonly fornecedorService = inject(SupplierService);
  private readonly fb = inject(FormBuilder);
  private readonly notification = inject(NotificationService);

  // Estado
  produtos = signal<Product[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  // Paginação (opcional, mantido para compatibilidade)
  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  filteredProdutos = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    const current = this.produtos();

    if (!Array.isArray(current)) return [];
    if (!term) return current;

    return current.filter(p =>
      p.nome?.toLowerCase().includes(term) ||
      p.codigo?.toLowerCase().includes(term) ||
      p.categoria?.toLowerCase().includes(term) ||
      p.marca?.toLowerCase().includes(term) ||
      p.gtin?.includes(term)
    );
  });

  // Modais e UI
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingProduto = signal<Product | null>(null);
  deletingProduto = signal<Product | null>(null);
  isSaving = signal(false);

  activeTab = signal<
    'geral' | 'comercial' | 'estoque' | 'fiscal' | 'dimensoes' | 'fornecedores' | 'imagens'
  >('geral');

  // Imagens
  imagemPrincipal = signal<string | null>(null);
  galeria = signal<string[]>([]);

  // Fornecedores (opções)
  fornecedoresOptions = signal<Supplier[]>([]);

  // Listas auxiliares
  readonly statusList = [
    { value: 'ATIVO', label: 'Ativo' },
    { value: 'INATIVO', label: 'Inativo' },
    { value: 'DESCONTINUADO', label: 'Descontinuado' }
  ];

  readonly categoriaList = [
    'Eletrônicos', 'Roupas', 'Alimentos', 'Bebidas', 'Limpeza',
    'Ferramentas', 'Móveis', 'Papelaria', 'Informática', 'Outros'
  ];

  readonly subcategoriaList = [
    'Smartphones', 'Notebooks', 'Camisetas', 'Calças', 'Carnes',
    'Laticínios', 'Detergentes', 'Chaves', 'Cadeiras', 'Canetas'
  ];

  readonly unidadeList = ['UN', 'KG', 'L', 'M', 'CX', 'PC', 'RL', 'FD'];

  readonly origemList = [
    { value: '0', label: '0 - Nacional' },
    { value: '1', label: '1 - Estrangeira - Importação direta' },
    { value: '2', label: '2 - Estrangeira - Adquirida no mercado interno' },
    { value: '3', label: '3 - Nacional com mais de 40% de conteúdo estrangeiro' },
    { value: '4', label: '4 - Nacional com processo produtivo básico' },
    { value: '5', label: '5 - Nacional com menos de 40% de conteúdo estrangeiro' },
    { value: '6', label: '6 - Estrangeira - Importação direta, sem similar nacional' },
    { value: '7', label: '7 - Estrangeira - Adquirida no mercado interno, sem similar nacional' },
    { value: '8', label: '8 - Nacional com mais de 70% de conteúdo estrangeiro' }
  ];

  readonly perfilTributarioList = [
    'Simples Nacional',
    'Lucro Presumido',
    'Lucro Real',
    'MEI',
    'Isento'
  ];

  // Formulário
  form: FormGroup = this.fb.group({
    // Geral
    codigo: ['', Validators.required],
    nome: ['', Validators.required],
    descricao: [''],
    categoria: [''],
    subcategoria: [''],
    marca: [''],
    modelo: [''],
    fabricante: [''],
    codigoFabricante: [''],
    gtin: [''],
    status: ['ATIVO', Validators.required],

    // Comercial
    precoVenda: [null as number | null],
    markup: [null as number | null],
    margem: [null as number | null],
    fornecedorPrincipal: [null as number | null],
    precoMinimo: [null as number | null],
    quantidadeMinima: [null as number | null],

    // Estoque
    unidade: ['UN'],
    controlaEstoque: [true],
    estoqueMinimo: [null as number | null],
    estoqueMaximo: [null as number | null],
    pontoReposicao: [null as number | null],
    lote: [''],
    validade: [''],
    numeroSerie: [''],

    // Fiscal
    ncm: [''],
    cest: [''],
    origem: [''],
    perfilTributario: [''],

    // Dimensões
    pesoBruto: [null as number | null],
    pesoLiquido: [null as number | null],
    altura: [null as number | null],
    largura: [null as number | null],
    comprimento: [null as number | null],

    // Fornecedores (FormArray)
    fornecedores: this.fb.array([])
  });

  get fornecedores(): FormArray {
    return this.form.get('fornecedores') as FormArray;
  }

  // Volume calculado
  volumeCalculado = computed(() => {
    const altura = this.form.get('altura')?.value;
    const largura = this.form.get('largura')?.value;
    const comprimento = this.form.get('comprimento')?.value;
    if (altura && largura && comprimento) {
      const volume = (altura * largura * comprimento) / 1000000; // cm³ → m³
      return volume.toFixed(4).replace('.', ',') + ' m³';
    }
    return '—';
  });

  ngOnInit(): void {
    this.loadProdutos();
    this.loadFornecedoresOptions();
  }

  loadProdutos(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.produtoService.findAll(this.currentPage(), this.pageSize()).subscribe({
      next: (page) => {
        this.produtos.set(page.content || []);
        this.totalElements.set(page.totalElements ?? 0);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Erro ao carregar produtos.');
        this.isLoading.set(false);
      }
    });
  }

  loadFornecedoresOptions(): void {
    this.fornecedorService.findAll(0, 1000).subscribe({
      next: (page) => {
        this.fornecedoresOptions.set(page.content || []);
      },
      error: () => {
        // silencioso, apenas não carrega opções
      }
    });
  }

  openCreateModal(): void {
    this.editingProduto.set(null);
    this.fornecedores.clear();
    this.form.reset({
      status: 'ATIVO',
      unidade: 'UN',
      controlaEstoque: true
    });
    this.imagemPrincipal.set(null);
    this.galeria.set([]);
    this.activeTab.set('geral');
    this.showFormModal.set(true);
  }

  openEditModal(produto: Product): void {
    this.editingProduto.set(produto);
    this.fornecedores.clear();

    // Popula fornecedores
    (produto.fornecedores || []).forEach(f => {
      this.fornecedores.push(this.buildFornecedorGroup(f));
    });

    // Popula imagens
    this.imagemPrincipal.set(produto.imagemPrincipal || null);
    this.galeria.set(produto.galeria || []);

    this.form.patchValue({
      codigo: produto.codigo ?? '',
      nome: produto.nome ?? '',
      descricao: produto.descricao ?? '',
      categoria: produto.categoria ?? '',
      subcategoria: produto.subcategoria ?? '',
      marca: produto.marca ?? '',
      modelo: produto.modelo ?? '',
      fabricante: produto.fabricante ?? '',
      codigoFabricante: produto.codigoFabricante ?? '',
      gtin: produto.gtin ?? '',
      status: produto.status ?? 'ATIVO',

      precoVenda: produto.precoVenda ?? null,
      markup: produto.markup ?? null,
      margem: produto.margem ?? null,
      fornecedorPrincipal: produto.fornecedorPrincipal ?? null,
      precoMinimo: produto.precoMinimo ?? null,
      quantidadeMinima: produto.quantidadeMinima ?? null,

      unidade: produto.unidade ?? 'UN',
      controlaEstoque: produto.controlaEstoque ?? true,
      estoqueMinimo: produto.estoqueMinimo ?? null,
      estoqueMaximo: produto.estoqueMaximo ?? null,
      pontoReposicao: produto.pontoReposicao ?? null,
      lote: produto.lote ?? '',
      validade: produto.validade ?? '',
      numeroSerie: produto.numeroSerie ?? '',

      ncm: produto.ncm ?? '',
      cest: produto.cest ?? '',
      origem: produto.origem ?? '',
      perfilTributario: produto.perfilTributario ?? '',

      pesoBruto: produto.pesoBruto ?? null,
      pesoLiquido: produto.pesoLiquido ?? null,
      altura: produto.altura ?? null,
      largura: produto.largura ?? null,
      comprimento: produto.comprimento ?? null
    });

    this.activeTab.set('geral');
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
    const raw = this.form.value;
    const payload: Product = {
      ...raw,
      precoVenda: raw.precoVenda != null ? Number(raw.precoVenda) : null,
      precoMinimo: raw.precoMinimo != null ? Number(raw.precoMinimo) : null,
      markup: raw.markup != null ? Number(raw.markup) : null,
      margem: raw.margem != null ? Number(raw.margem) : null,
      quantidadeMinima: raw.quantidadeMinima != null ? Number(raw.quantidadeMinima) : null,
      estoqueMinimo: raw.estoqueMinimo != null ? Number(raw.estoqueMinimo) : null,
      estoqueMaximo: raw.estoqueMaximo != null ? Number(raw.estoqueMaximo) : null,
      pontoReposicao: raw.pontoReposicao != null ? Number(raw.pontoReposicao) : null,
      pesoBruto: raw.pesoBruto != null ? Number(raw.pesoBruto) : null,
      pesoLiquido: raw.pesoLiquido != null ? Number(raw.pesoLiquido) : null,
      altura: raw.altura != null ? Number(raw.altura) : null,
      largura: raw.largura != null ? Number(raw.largura) : null,
      comprimento: raw.comprimento != null ? Number(raw.comprimento) : null,
      fornecedores: (raw.fornecedores || []).filter((f: ProductSupplier) => f.fornecedorId != null),
      imagemPrincipal: this.imagemPrincipal(),
      galeria: this.galeria()
    };

    const editing = this.editingProduto();

    if (editing?.id) {
      this.produtoService.update(editing.id, payload).subscribe({
        next: (updated) => {
          this.produtos.update(list => list.map(p => (p.id === editing.id ? updated : p)));
          this.notification.success('Produto atualizado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao atualizar produto.');
          this.isSaving.set(false);
        }
      });
    } else {
      this.produtoService.create(payload).subscribe({
        next: (created) => {
          this.produtos.update(list => [created, ...list]);
          this.notification.success('Produto criado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao criar produto.');
          this.isSaving.set(false);
        }
      });
    }
  }

  confirmDelete(produto: Product): void {
    this.deletingProduto.set(produto);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deletingProduto.set(null);
  }

  deleteProduto(): void {
    const produto = this.deletingProduto();
    if (!produto?.id) return;

    this.produtoService.delete(produto.id).subscribe({
      next: () => {
        this.produtos.update(list => list.filter(p => p.id !== produto.id));
        this.notification.success('Produto excluído com sucesso!');
        this.closeDeleteModal();
      },
      error: () => this.notification.error('Erro ao excluir produto.')
    });
  }

  // ==================== Fornecedores (FormArray) ====================
  private buildFornecedorGroup(f: ProductSupplier = {
    fornecedorId: null,
    codigoFornecedor: '',
    preco: undefined,
    prazo: undefined,
    principal: false
  }): FormGroup {
    return this.fb.group({
      fornecedorId: [f.fornecedorId, Validators.required],
      codigoFornecedor: [f.codigoFornecedor ?? ''],
      preco: [f.preco ?? null],
      prazo: [f.prazo ?? null],
      principal: [f.principal ?? false]
    });
  }

  addFornecedor(): void {
    this.fornecedores.push(this.buildFornecedorGroup());
  }

  removeFornecedor(index: number): void {
    this.fornecedores.removeAt(index);
  }

  setPrincipal(index: number): void {
    this.fornecedores.controls.forEach((ctrl, i) => {
      ctrl.get('principal')?.setValue(i === index);
    });
  }

  onFornecedorPrincipalChange(fornecedorId: any): void {
    // Sincroniza a seleção principal com o FormArray, se o fornecedor existir lá
    if (!fornecedorId) return;
    this.fornecedores.controls.forEach(ctrl => {
      const id = ctrl.get('fornecedorId')?.value;
      ctrl.get('principal')?.setValue(id == fornecedorId);
    });
  }

  // ==================== Toggle ====================
  toggleControlaEstoque(): void {
    const ctrl = this.form.get('controlaEstoque');
    ctrl?.setValue(!ctrl.value);
  }

  // ==================== Imagens ====================
  onImagemPrincipalSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.readFile(file).then(dataUrl => this.imagemPrincipal.set(dataUrl));
    input.value = '';
  }

  removeImagemPrincipal(): void {
    this.imagemPrincipal.set(null);
  }

  onGaleriaSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files) return;

    Array.from(files).forEach(file => {
      this.readFile(file).then(dataUrl => {
        this.galeria.update(prev => [...prev, dataUrl]);
      });
    });
    input.value = '';
  }

  removeGaleriaImage(index: number): void {
    this.galeria.update(prev => prev.filter((_, i) => i !== index));
  }

  private readFile(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  // ==================== Máscaras e Formatações ====================
  formatCurrency(event: Event, controlName: string): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (!value) {
      this.form.get(controlName)?.setValue(null, { emitEvent: false });
      return;
    }
    const numberValue = Number(value) / 100;
    const formatted = numberValue.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    });
    this.form.get(controlName)?.setValue(formatted, { emitEvent: false });
  }

  formatCurrencyControl(ctrl: AbstractControl | null, event: Event): void {
    if (!ctrl) return;
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (!value) {
      ctrl.setValue(null, { emitEvent: false });
      return;
    }
    const numberValue = Number(value) / 100;
    const formatted = numberValue.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    });
    ctrl.setValue(formatted, { emitEvent: false });
  }

  formatGtin(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 14) value = value.substring(0, 14);
    this.form.get('gtin')?.setValue(value, { emitEvent: false });
  }

  formatNcm(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 8) value = value.substring(0, 8);
    value = value.replace(/^(\d{4})(\d)/, '$1.$2');
    value = value.replace(/^(\d{4})\.(\d{2})(\d)/, '$1.$2.$3');
    this.form.get('ncm')?.setValue(value, { emitEvent: false });
  }

  formatCest(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 7) value = value.substring(0, 7);
    value = value.replace(/^(\d{2})(\d)/, '$1.$2');
    value = value.replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3');
    this.form.get('cest')?.setValue(value, { emitEvent: false });
  }

  // ==================== Helpers ====================
  statusLabel(status: string): string {
    const found = this.statusList.find(s => s.value === status);
    return found ? found.label : status;
  }

  formatMoney(value: number | null | undefined): string {
    if (value == null) return '—';
    return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
