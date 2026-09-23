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
import { ProductImageService } from '../../../core/services/product-image-service';
import { ProductImageItem } from '../../models/product-image-item';
import { firstValueFrom } from 'rxjs';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-products-component',
  styleUrl: './products-component.scss',
  templateUrl: './products-component.html',
})
export class ProductsComponent implements OnInit {
  private readonly produtoService =
  inject(ProductService);

private readonly produtoImageService =
  inject(ProductImageService);

private readonly fornecedorService =
  inject(SupplierService);

private readonly fb =
  inject(FormBuilder);

private readonly notification =
  inject(NotificationService);

  /* ---------- Estado ---------- */
  produtos = signal<Product[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  filteredProdutos = computed(() => {

    const term =
      this.searchTerm().trim();

    const normalizedTerm =
      this.normalizeSearchValue(term);

    const list =
      this.produtos();

    if (!Array.isArray(list)) {
      return [];
    }

    if (!term) {
      return list;
    }

    return list.filter((produto) => {

      const name =
        this.normalizeSearchValue(
          produto.name ?? ''
        );

      const code =
        this.normalizeSearchValue(
          produto.code ?? ''
        );

      const model =
        this.normalizeSearchValue(
          produto.model ?? ''
        );

      const manufacturerCode =
        this.normalizeSearchValue(
          produto.manufacturerCode ?? ''
        );

      const gtin =
        this.normalizeSearchValue(
          produto.gtin ?? ''
        );

      return (
        name.includes(normalizedTerm) ||
        code.includes(normalizedTerm) ||
        model.includes(normalizedTerm) ||
        manufacturerCode.includes(normalizedTerm) ||
        gtin.includes(normalizedTerm)
      );
    });
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
  images = signal<ProductImageItem[]>([]);

  private readonly maxImageSize =
    5 * 1024 * 1024;

  private readonly allowedImageTypes =
    new Set([
      'image/jpeg',
      'image/png',
      'image/webp',
    ]);

  private removedImageIds = new Set<number>();

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
        .filter((s) => s.id != null)
        .map((s) => ({
          id: s.id,
          name:
            s.person?.tipoPessoa === 'PJ'
              ? s.legalEntity?.nomeFantasia?.trim() ||
                s.person?.name?.trim() ||
                `Fornecedor #${s.id}`
              : s.person?.name?.trim() ||
                `Fornecedor #${s.id}`,
        }));

      this.supplierOptions.set(options);
    },

    error: (error) => {
      console.error('Erro ao carregar fornecedores:', error);
      this.supplierOptions.set([]);
    },
  });
}

  /* ==================== Abrir / Fechar modais ==================== */
  openCreateModal(): void {
    this.editingProduto.set(null);
    this.suppliers.clear();
    this.clearImageState();

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
    this.clearImageState();

    // Fornecedores
    (produto.suppliers || []).forEach((s) => {
      this.suppliers.push(this.buildSupplierGroup(s));
    });

    // Imagens
    const serverImages =
    (produto.images || []).map(
      (img, index): ProductImageItem => ({
        key: `server-${img.id}`,

        id: img.id,

        fileName: img.fileName,

        contentType:
          img.contentType ?? null,

        fileSize:
          img.fileSize ?? undefined,

        mainImage:
          !!img.mainImage,

        sortOrder:
          img.sortOrder ?? index,

        url:
          img.url,

        previewUrl: '',
      })
    );
    this.images.set(serverImages);

    if (produto.id) {
      serverImages.forEach((image) => {
        this.loadImagePreview(
          produto.id!,
          image
        );
      });
    }

    this.form.patchValue({
      code:
        produto.code ?? '',
      name:
        produto.name ?? '',
      description:
        produto.description ?? '',
      model:
        produto.model ?? '',
      manufacturerCode:
        produto.manufacturerCode ?? '',
      gtin:
        produto.gtin ?? '',
      status:
        produto.status ?? 'ATIVO',
      salePrice:
        this.formatCurrencyValue(
          produto.salePrice
        ),
      minimumSalePrice:
        this.formatCurrencyValue(
          produto.minimumSalePrice
        ),
      unitOfMeasure:
        produto.unitOfMeasure ?? 'UN',
      controlsStock:
        produto.controlsStock ?? true,
      minimumStock:
        produto.minimumStock ?? null,
      maximumStock:
        produto.maximumStock ?? null,
      reorderPoint:
        produto.reorderPoint ?? null,
      ncm:
        this.applyNcmMask(
          produto.ncm
        ),
      cest:
        this.applyCestMask(
          produto.cest
        ),
      origin:
        produto.origin ?? '',
      grossWeight:
        produto.grossWeight ?? null,
      netWeight:
        produto.netWeight ?? null,
      height:
        produto.height ?? null,
      width:
        produto.width ?? null,
      length:
        produto.length ?? null,
    });

    this.activeTab.set('geral');
    this.showFormModal.set(true);
  }

  closeFormModal(): void {

    this.clearImageState();

    this.showFormModal.set(false);
  }

  /* ==================== Submissão ==================== */
  async onSubmit(): Promise<void> {

    if (this.form.invalid) {

      this.form.markAllAsTouched();

      this.notification.show(
        'Preencha todos os campos obrigatórios.',
        'warning'
      );

      return;
    }

    this.isSaving.set(true);

    const payload =
      this.buildRequest();

    const editing =
      this.editingProduto();

    if (editing?.id) {

      this.produtoService
        .update(
          editing.id,
          payload
        )
        .subscribe({

          next: async (updated) => {

            try {

              await this.syncImages(
                editing.id!
              );

              this.notification.success(
                'Produto atualizado com sucesso!'
              );

              this.loadProdutos();

              this.closeFormModal();

            } catch (error) {

              console.error(
                'Erro ao sincronizar imagens:',
                error
              );

              this.notification.error(
                'Produto atualizado, mas houve erro ao processar as imagens.'
              );

            } finally {

              this.isSaving.set(false);
            }
          },

          error: () => {

            this.notification.error(
              'Erro ao atualizar produto.'
            );

            this.isSaving.set(false);
          },
        });

    } else {

      this.produtoService
        .create(payload)
        .subscribe({

          next: async (created) => {

            try {

              if (!created.id) {
                throw new Error(
                  'O produto foi criado sem retornar o ID.'
                );
              }

              await this.syncImages(
                created.id
              );

              this.notification.success(
                'Produto criado com sucesso!'
              );

              this.loadProdutos();

              this.closeFormModal();

            } catch (error) {

              console.error(
                'Erro ao sincronizar imagens:',
                error
              );

              this.notification.error(
                'Produto criado, mas houve erro ao processar as imagens.'
              );

            } finally {

              this.isSaving.set(false);
            }
          },

          error: () => {

            this.notification.error(
              'Erro ao criar produto.'
            );

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

      // Envia somente os dígitos
      ncm: this.onlyDigits(v.ncm),
      cest: this.onlyDigits(v.cest),

      origin: v.origin || null,
      grossWeight: this.toNumberOrNull(v.grossWeight),
      netWeight: this.toNumberOrNull(v.netWeight),
      height: this.toNumberOrNull(v.height),
      width: this.toNumberOrNull(v.width),
      length: this.toNumberOrNull(v.length),

      suppliers: suppliersRequest,
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
  private buildSupplierGroup(
    s: Partial<ProductSupplier> = {}
  ): FormGroup {

    return this.fb.group({

      supplierId: [
        s.supplierId ?? null,
        Validators.required
      ],

      supplierCode: [
        s.supplierCode ?? ''
      ],

      purchasePrice: [
        this.formatCurrencyValue(
          s.purchasePrice
        )
      ],

      leadTimeDays: [
        s.leadTimeDays ?? null
      ],

      minimumOrderQuantity: [
        s.minimumOrderQuantity ?? null
      ],

      preferred: [
        !!s.preferred
      ],

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

    const input =
      event.target as HTMLInputElement;

    const files =
      input.files;

    if (!files || files.length === 0) {
      return;
    }

    const current =
      this.images();

    const hasMainImage =
      current.some(
        (image) => image.mainImage
      );

    const validImages:
      ProductImageItem[] = [];

    Array.from(files).forEach((file) => {

      if (!this.allowedImageTypes.has(file.type)) {

        this.notification.show(
          `O arquivo "${file.name}" não é suportado.`
        );

        return;
      }

      if (file.size > this.maxImageSize) {

        this.notification.show(
          `A imagem "${file.name}" excede o limite de 5 MB.`
        );

        return;
      }

      const previewUrl =
        URL.createObjectURL(file);

      validImages.push({

        key:
          crypto.randomUUID(),

        fileName:
          file.name,

        contentType:
          file.type || null,

        fileSize:
          file.size,

        mainImage:
          !hasMainImage &&
          validImages.length === 0,

        sortOrder:
          current.length +
          validImages.length,

        previewUrl,

        pendingFile:
          file,
      });
    });

    if (validImages.length > 0) {

      this.images.update((prev) => [
        ...prev,
        ...validImages,
      ]);
    }

    input.value = '';
  }

  removeImage(index: number): void {

    const current =
      this.images();

    const removed =
      current[index];

    if (!removed) {
      return;
    }

    if (removed.id) {
      this.removedImageIds.add(
        removed.id
      );
    }

    this.revokePreview(
      removed.previewUrl
    );

    const remaining =
      current.filter(
        (_, i) => i !== index
      );

    if (
      removed.mainImage &&
      remaining.length > 0
    ) {

      remaining[0] = {
        ...remaining[0],
        mainImage: true,
      };
    }

    this.images.set(
      remaining.map((image, i) => ({
        ...image,
        sortOrder: i,
      }))
    );
  }

  setMainImage(index: number): void {

    this.images.update((images) =>
      images.map((image, i) => ({
        ...image,
        mainImage:
          i === index,
      }))
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

    const input =
      event.target as HTMLInputElement;

    const value =
      this.applyNcmMask(
        input.value
      );

    this.form
      .get('ncm')
      ?.setValue(
        value,
        {
          emitEvent: false
        }
      );

    input.value = value;
  }

  private applyNcmMask(
    value: string | null | undefined
  ): string {

    if (!value) {
      return '';
    }

    let str =
      value.replace(/\D/g, '');

    if (str.length > 8) {
      str = str.substring(0, 8);
    }

    return str
      .replace(
        /^(\d{4})(\d)/,
        '$1.$2'
      )
      .replace(
        /^(\d{4})\.(\d{2})(\d)/,
        '$1.$2.$3'
      );
  }

  formatCest(event: Event): void {

    const input =
      event.target as HTMLInputElement;

    const value =
      this.applyCestMask(
        input.value
      );

    this.form
      .get('cest')
      ?.setValue(
        value,
        {
          emitEvent: false
        }
      );

    input.value = value;
  }

  private applyCestMask(
    value: string | null | undefined
  ): string {

    if (!value) {
      return '';
    }

    let str =
      value.replace(/\D/g, '');

    if (str.length > 7) {
      str = str.substring(0, 7);
    }

    return str
      .replace(
        /^(\d{2})(\d)/,
        '$1.$2'
      )
      .replace(
        /^(\d{2})\.(\d{3})(\d)/,
        '$1.$2.$3'
      );
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

  /* ==================== Helpers ==================== */
  private toNumberOrNull(
    value: unknown
  ): number | null {

    if (
      value === null ||
      value === undefined ||
      value === ''
    ) {
      return null;
    }

    if (typeof value === 'number') {
      return Number.isFinite(value)
        ? value
        : null;
    }

    let str =
      String(value).trim();

    if (!str) {
      return null;
    }

    // Formato brasileiro:
    // R$ 1.234,56
    if (str.includes(',')) {

      str = str
        .replace(/[^\d,-]/g, '')
        .replace(/\./g, '')
        .replace(',', '.');

    } else {

      // Número sem separador decimal brasileiro
      str = str.replace(
        /[^0-9.-]/g,
        ''
      );
    }

    const number =
      Number(str);

    return Number.isFinite(number)
      ? number
      : null;
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

  private onlyDigits(value: unknown): string | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }

    const digits = String(value).replace(/\D/g, '');

    return digits || null;
  }

  private normalizeSearchValue(
    value: string
  ): string {

    return value
      .normalize('NFD')
      .replace(
        /[\u0300-\u036f]/g,
        ''
      )
      .toLowerCase()
      .replace(
        /[^a-z0-9]/g,
        ''
      );
  }

  private loadImagePreview(
    productId: number,
    image: ProductImageItem
  ): void {

    if (!image.id) {
      return;
    }

    this.produtoImageService
      .getContent(
        productId,
        image.id
      )
      .subscribe({
        next: (blob) => {

          const previewUrl =
            URL.createObjectURL(blob);

          this.images.update((items) =>
            items.map((item) =>
              item.key === image.key
                ? {
                    ...item,
                    previewUrl,
                  }
                : item
            )
          );
        },

        error: () => {
          this.notification.error(
            `Não foi possível carregar a imagem "${image.fileName}".`
          );
        },
      });
  }

  private revokePreview(
    previewUrl: string | undefined
  ): void {

    if (
      previewUrl &&
      previewUrl.startsWith('blob:')
    ) {
      URL.revokeObjectURL(
        previewUrl
      );
    }
  }

  private clearImageState(): void {

    this.images()
      .forEach((image) =>
        this.revokePreview(
          image.previewUrl
        )
      );

    this.images.set([]);

    this.removedImageIds.clear();
  }

  private async syncImages(
    productId: number
  ): Promise<void> {

    // ==========================================================
    // 1. Excluir imagens removidas pelo usuário
    // ==========================================================

    const removedIds =
      Array.from(
        this.removedImageIds
      );

    for (const imageId of removedIds) {

      await firstValueFrom(
        this.produtoImageService.delete(
          productId,
          imageId
        )
      );

      this.removedImageIds.delete(
        imageId
      );
    }

    // ==========================================================
    // 2. Captura qual imagem o usuário escolheu como principal
    // ==========================================================

    const desiredMain =
      this.images().find(
        (image) => image.mainImage
      );

    const desiredMainKey =
      desiredMain?.key;

    // ==========================================================
    // 3. Enviar arquivos ainda pendentes
    // ==========================================================

    const pendingImages =
      this.images()
        .filter(
          (image) => !!image.pendingFile
        )
        .sort(
          (a, b) =>
            a.sortOrder -
            b.sortOrder
        );

    for (const image of pendingImages) {

      if (!image.pendingFile) {
        continue;
      }

      const saved =
        await firstValueFrom(
          this.produtoImageService.upload(
            productId,
            image.pendingFile
          )
        );

      this.images.update((items) =>
        items.map((item) =>
          item.key === image.key
            ? {
                ...item,

                id:
                  saved.id,

                fileName:
                  saved.fileName,

                contentType:
                  saved.contentType,

                fileSize:
                  saved.fileSize,

                mainImage:
                  saved.mainImage,

                sortOrder:
                  saved.sortOrder,

                url:
                  saved.url,

                pendingFile:
                  undefined,
              }
            : item
        )
      );
    }

    // ==========================================================
    // 4. Define definitivamente a imagem principal
    // ==========================================================

    if (desiredMainKey) {

      const finalMain =
        this.images().find(
          (image) =>
            image.key ===
            desiredMainKey
        );

      if (finalMain?.id) {

        await firstValueFrom(
          this.produtoImageService
            .setMainImage(
              productId,
              finalMain.id
            )
        );
      }
    }
  }


}
