import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ProductService } from '../../../core/services/product-service';
import { SupplierService } from '../../../core/services/supplier-service';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { Product } from '../../models/product';
import { ProductImage } from '../../models/product-image';
import { ProductStatus } from '../../models/product-status';
import { ProductRequest } from '../../models/product-request';
import { ProductSupplierRequest } from '../../models/product-supplier-request';
import { ProductImageRequest } from '../../models/product-image-request';
import { ProductSupplier } from '../../models/product-supplier';
import { CommonModule } from '@angular/common';
import { SupplierOption } from '../../models/supplier-option';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-products-component',
  styleUrl: './products-component.scss',
  templateUrl: './products-component.html',
})
export class ProductsComponent implements OnInit {
  private readonly produtoService = inject(ProductService);
  private readonly fornecedorService = inject(SupplierService);
  private readonly fb = inject(FormBuilder);
  private readonly notification = inject(NotificationService);

  /* ---------- Estado ---------- */
  produtos = signal<Product[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  filteredProdutos = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    const list = this.produtos();
    if (!Array.isArray(list)) return [];
    if (!term) return list;

    return list.filter(
      (p) =>
        p.name?.toLowerCase().includes(term) ||
        p.code?.toLowerCase().includes(term) ||
        p.model?.toLowerCase().includes(term) ||
        p.manufacturerCode?.toLowerCase().includes(term) ||
        p.gtin?.includes(term),
    );
  });

  /* ---------- Modais / UI ---------- */
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingProduto = signal<Product | null>(null);
  deletingProduto = signal<Product | null>(null);
  isSaving = signal(false);

  activeTab = signal<
    'geral' | 'comercial' | 'estoque' | 'fiscal' | 'dimensoes' | 'fornecedores' | 'imagens'
  >('geral');

  /* ---------- Imagens (signal, não FormArray — envio de arquivos) ---------- */
  images = signal<ProductImage[]>([]);

  /* ---------- Fornecedores (opções para selects) ---------- */
  supplierOptions = signal<SupplierOption[]>([]);

  /* ---------- Listas auxiliares ---------- */
  readonly statusList: { value: ProductStatus; label: string }[] = [
    { value: 'ATIVO', label: 'Ativo' },
    { value: 'INATIVO', label: 'Inativo' },
    { value: 'DESCONTINUADO', label: 'Descontinuado' },
  ];

  readonly unitOfMeasureList = ['UN', 'KG', 'L', 'M', 'M2', 'M3', 'CX', 'PC', 'RL', 'FD', 'PAR'];

  readonly originList = [
    { value: '0', label: '0 - Nacional' },
    { value: '1', label: '1 - Estrangeira - Importação direta' },
    { value: '2', label: '2 - Estrangeira - Adquirida no mercado interno' },
    { value: '3', label: '3 - Nacional com mais de 40% de conteúdo estrangeiro' },
    { value: '4', label: '4 - Nacional com processo produtivo básico' },
    { value: '5', label: '5 - Nacional com menos de 40% de conteúdo estrangeiro' },
    { value: '6', label: '6 - Estrangeira - Importação direta, sem similar nacional' },
    { value: '7', label: '7 - Estrangeira - Adquirida no mercado interno, sem similar nacional' },
    { value: '8', label: '8 - Nacional com mais de 70% de conteúdo estrangeiro' },
  ];

  /* ---------- Formulário ---------- */
  form: FormGroup = this.fb.group({
    // Geral
    code: ['', Validators.required],
    name: ['', Validators.required],
    description: [''],
    model: [''],
    manufacturerCode: [''],
    gtin: [''],
    status: ['ATIVO' as ProductStatus, Validators.required],

    // Comercial
    salePrice: [null as number | null],
    minimumSalePrice: [null as number | null],

    // Estoque
    unitOfMeasure: ['UN', Validators.required],
    controlsStock: [true],
    minimumStock: [null as number | null],
    maximumStock: [null as number | null],
    reorderPoint: [null as number | null],

    // Fiscal
    ncm: [''],
    cest: [''],
    origin: [''],

    // Dimensões
    grossWeight: [null as number | null],
    netWeight: [null as number | null],
    height: [null as number | null],
    width: [null as number | null],
    length: [null as number | null],

    // Fornecedores
    suppliers: this.fb.array([]),
  });

  get suppliers(): FormArray {
    return this.form.get('suppliers') as FormArray;
  }

  /* ---------- Volume calculado (m³) ---------- */
  volumeCalculado = computed(() => {
    const h = Number(this.form.get('height')?.value) || 0;
    const w = Number(this.form.get('width')?.value) || 0;
    const l = Number(this.form.get('length')?.value) || 0;
    if (h > 0 && w > 0 && l > 0) {
      const volume = (h * w * l) / 1_000_000;
      return volume.toFixed(4).replace('.', ',') + ' m³';
    }
    return '—';
  });

  ngOnInit(): void {
    this.loadProdutos();
    this.loadSupplierOptions();
  }

  /* ==================== Carregamento ==================== */
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
        this.isLoading.set(false);
      },
    });
  }

  loadSupplierOptions(): void {
  this.fornecedorService.findAll(0, 1000).subscribe({
    next: (page) => {
      const content = page.content ?? [];
      const options: SupplierOption[] = content
        .filter((s: any) => s.id != null)
        .map((s: any) => ({ id: s.id, name: s.name }));

      this.supplierOptions.set(options);
    },
    error: () => {
      /* silencioso */
    },
  });
}

  /* ==================== Abrir / Fechar modais ==================== */
  openCreateModal(): void {
    this.editingProduto.set(null);
    this.suppliers.clear();
    this.images.set([]);

    this.form.reset({
      code: '',
      name: '',
      description: '',
      model: '',
      manufacturerCode: '',
      gtin: '',
      status: 'ATIVO',
      salePrice: null,
      minimumSalePrice: null,
      unitOfMeasure: 'UN',
      controlsStock: true,
      minimumStock: null,
      maximumStock: null,
      reorderPoint: null,
      ncm: '',
      cest: '',
      origin: '',
      grossWeight: null,
      netWeight: null,
      height: null,
      width: null,
      length: null,
    });

    this.activeTab.set('geral');
    this.showFormModal.set(true);
  }

  openEditModal(produto: Product): void {
    this.editingProduto.set(produto);
    this.suppliers.clear();
    this.images.set([]);

    // Fornecedores
    (produto.suppliers || []).forEach((s) => {
      this.suppliers.push(this.buildSupplierGroup(s));
    });

    // Imagens
    this.images.set(
      (produto.images || []).map((img, idx) => ({
        ...img,
        sortOrder: img.sortOrder ?? idx,
        mainImage: !!img.mainImage,
      })),
    );

    this.form.patchValue({
      code: produto.code ?? '',
      name: produto.name ?? '',
      description: produto.description ?? '',
      model: produto.model ?? '',
      manufacturerCode: produto.manufacturerCode ?? '',
      gtin: produto.gtin ?? '',
      status: produto.status ?? 'ATIVO',

      salePrice: produto.salePrice ?? null,
      minimumSalePrice: produto.minimumSalePrice ?? null,

      unitOfMeasure: produto.unitOfMeasure ?? 'UN',
      controlsStock: produto.controlsStock ?? true,
      minimumStock: produto.minimumStock ?? null,
      maximumStock: produto.maximumStock ?? null,
      reorderPoint: produto.reorderPoint ?? null,

      ncm: produto.ncm ?? '',
      cest: produto.cest ?? '',
      origin: produto.origin ?? '',

      grossWeight: produto.grossWeight ?? null,
      netWeight: produto.netWeight ?? null,
      height: produto.height ?? null,
      width: produto.width ?? null,
      length: produto.length ?? null,
    });

    this.activeTab.set('geral');
    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    this.showFormModal.set(false);
  }

  /* ==================== Submissão ==================== */
  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show('Preencha todos os campos obrigatórios.', 'warning');
      return;
    }

    this.isSaving.set(true);
    const payload = this.buildRequest();

    const editing = this.editingProduto();
    if (editing?.id) {
      this.produtoService.update(editing.id, payload).subscribe({
        next: (updated) => {
          this.produtos.update((list) =>
            list.map((p) => (p.id === editing.id ? updated : p)),
          );
          this.notification.success('Produto atualizado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao atualizar produto.');
          this.isSaving.set(false);
        },
      });
    } else {
      this.produtoService.create(payload).subscribe({
        next: (created) => {
          this.produtos.update((list) => [created, ...list]);
          this.notification.success('Produto criado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.isSaving.set(false);
        },
      });
    }
  }

  private buildRequest(): ProductRequest {
    const v = this.form.value;

    const suppliersRequest: ProductSupplierRequest[] = (v.suppliers || [])
      .filter((s: any) => s.supplierId != null && s.supplierId !== '')
      .map((s: any) => ({
        supplierId: Number(s.supplierId),
        supplierCode: s.supplierCode || null,
        purchasePrice: this.toNumberOrNull(s.purchasePrice),
        leadTimeDays: this.toNumberOrNull(s.leadTimeDays),
        minimumOrderQuantity: this.toNumberOrNull(s.minimumOrderQuantity),
        preferred: !!s.preferred,
      }));

    const imagesRequest: ProductImageRequest[] = this.images()
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((img, idx) => ({
        fileName: img.fileName,
        storagePath: img.storagePath,
        contentType: img.contentType ?? null,
        mainImage: !!img.mainImage,
        sortOrder: idx,
      }));

    return {
      code: v.code?.trim(),
      name: v.name?.trim(),
      description: v.description || null,
      model: v.model || null,
      manufacturerCode: v.manufacturerCode || null,
      gtin: v.gtin || null,
      status: v.status,
      unitOfMeasure: v.unitOfMeasure,
      controlsStock: !!v.controlsStock,
      salePrice: this.toNumberOrNull(v.salePrice),
      minimumSalePrice: this.toNumberOrNull(v.minimumSalePrice),
      minimumStock: this.toNumberOrNull(v.minimumStock),
      maximumStock: this.toNumberOrNull(v.maximumStock),
      reorderPoint: this.toNumberOrNull(v.reorderPoint),
      ncm: v.ncm || null,
      cest: v.cest || null,
      origin: v.origin || null,
      grossWeight: this.toNumberOrNull(v.grossWeight),
      netWeight: this.toNumberOrNull(v.netWeight),
      height: this.toNumberOrNull(v.height),
      width: this.toNumberOrNull(v.width),
      length: this.toNumberOrNull(v.length),
      suppliers: suppliersRequest,
      images: imagesRequest,
    };
  }

  /* ==================== Exclusão ==================== */
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
        this.produtos.update((list) => list.filter((p) => p.id !== produto.id));
        this.notification.success('Produto excluído com sucesso!');
        this.closeDeleteModal();
      },
      error: () => this.notification.error('Erro ao excluir produto.'),
    });
  }

  /* ==================== FormArray — Fornecedores ==================== */
  private buildSupplierGroup(s: Partial<ProductSupplier> = {}): FormGroup {
    return this.fb.group({
      supplierId: [s.supplierId ?? null, Validators.required],
      supplierCode: [s.supplierCode ?? ''],
      purchasePrice: [s.purchasePrice ?? null],
      leadTimeDays: [s.leadTimeDays ?? null],
      minimumOrderQuantity: [s.minimumOrderQuantity ?? null],
      preferred: [!!s.preferred],
    });
  }

  addSupplier(): void {
    this.suppliers.push(this.buildSupplierGroup());
  }

  removeSupplier(index: number): void {
    this.suppliers.removeAt(index);
  }

  setPreferredSupplier(index: number): void {
    this.suppliers.controls.forEach((ctrl, i) => {
      ctrl.get('preferred')?.setValue(i === index, { emitEvent: false });
    });
  }

  /* ==================== Toggle ==================== */
  toggleControlsStock(): void {
    const ctrl = this.form.get('controlsStock');
    ctrl?.setValue(!ctrl.value);
  }

  /* ==================== Imagens ==================== */
  onImagesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files || files.length === 0) return;

    const current = this.images();
    const isFirstEver = current.length === 0;

    Array.from(files).forEach((file, i) => {
      this.readFileAsDataUrl(file).then((dataUrl) => {
        const next: ProductImage = {
          fileName: file.name,
          storagePath: dataUrl,
          contentType: file.type || null,
          mainImage: isFirstEver && i === 0,
          sortOrder: this.images().length,
        };
        this.images.update((prev) => [...prev, next]);
      });
    });

    input.value = '';
  }

  removeImage(index: number): void {
    this.images.update((prev) => {
      const removed = prev[index];
      const filtered = prev.filter((_, i) => i !== index);

      // Reordena
      const reordered = filtered.map((img, i) => ({ ...img, sortOrder: i }));

      // Se removeu a principal, promove a primeira
      if (removed?.mainImage && reordered.length > 0) {
        reordered[0].mainImage = true;
      }
      return reordered;
    });
  }

  setMainImage(index: number): void {
    this.images.update((prev) =>
      prev.map((img, i) => ({ ...img, mainImage: i === index })),
    );
  }

  private readFileAsDataUrl(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  /* ==================== Máscaras / Formatações ==================== */
  formatCurrency(event: Event, controlName: string): void {
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '');
    if (!digits) {
      this.form.get(controlName)?.setValue(null, { emitEvent: false });
      return;
    }
    const value = Number(digits) / 100;
    this.form.get(controlName)?.setValue(value, { emitEvent: false });
    input.value = value.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    });
  }

  formatCurrencyControl(ctrl: AbstractControl | null, event: Event): void {
    if (!ctrl) return;
    const input = event.target as HTMLInputElement;
    const digits = input.value.replace(/\D/g, '');
    if (!digits) {
      ctrl.setValue(null, { emitEvent: false });
      return;
    }
    const value = Number(digits) / 100;
    ctrl.setValue(value, { emitEvent: false });
    input.value = value.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    });
  }

  formatGtin(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '').substring(0, 14);
    this.form.get('gtin')?.setValue(value, { emitEvent: false });
    input.value = value;
  }

  formatNcm(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '').substring(0, 8);
    value = value.replace(/^(\d{4})(\d)/, '$1.$2');
    value = value.replace(/^(\d{4})\.(\d{2})(\d)/, '$1.$2.$3');
    this.form.get('ncm')?.setValue(value, { emitEvent: false });
    input.value = value;
  }

  formatCest(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '').substring(0, 7);
    value = value.replace(/^(\d{2})(\d)/, '$1.$2');
    value = value.replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3');
    this.form.get('cest')?.setValue(value, { emitEvent: false });
    input.value = value;
  }

  /* ==================== Helpers ==================== */
  private toNumberOrNull(v: any): number | null {
    if (v === null || v === undefined || v === '') return null;
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }

  statusLabel(status: ProductStatus | string | null | undefined): string {
    const found = this.statusList.find((s) => s.value === status);
    return found ? found.label : (status ?? '—');
  }

  formatMoney(value: number | null | undefined): string {
    if (value == null) return '—';
    return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  supplierName(id: number | null | undefined): string {
    if (id == null) return '—';
    return this.supplierOptions().find((s) => s.id === id)?.name ?? `#${id}`;
  }


}
