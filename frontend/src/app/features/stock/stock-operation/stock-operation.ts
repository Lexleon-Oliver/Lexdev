import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { NotificationService } from '../../../core/services/notification-service';
import { ProductService } from '../../../core/services/product-service';
import { StockService } from '../../../core/services/stock-service';
import { Product } from '../../models/product';
import { StockBalance, StockMovement, StockMovementType, StockOperationRequest } from '../models/stock';

export type StockOperationKind = 'INITIAL' | 'POSITIVE' | 'NEGATIVE';

@Component({
  selector: 'app-stock-operation',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './stock-operation.html',
  styleUrl: './stock-operation.scss',
})
export class StockOperationComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly stockService = inject(StockService);
  private readonly notification = inject(NotificationService);
  private readonly fb = inject(FormBuilder);

  readonly products = signal<Product[]>([]);
  readonly productsLoading = signal(false);
  readonly currentPage = signal(0);
  readonly pageSize = 20;
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  readonly selectedProduct = signal<Product | null>(null);
  readonly balance = signal<StockBalance | null>(null);
  readonly movements = signal<StockMovement[]>([]);
  readonly stockLoading = signal(false);
  readonly movementsPage = signal(0);
  readonly movementsTotalPages = signal(0);

  readonly operation = signal<StockOperationKind | null>(null);
  readonly saving = signal(false);
  private operationReference: string | null = null;

  readonly controlledProducts = computed(() => this.products().filter(product => product.controlsStock));
  readonly canInitialize = computed(() => this.balance()?.controlsStock === true && this.balance()?.initialized === false);
  readonly canAdjust = computed(() => this.balance()?.controlsStock === true && this.balance()?.initialized === true);

  readonly form = this.fb.group({
    quantity: [null as number | null, [Validators.required, Validators.min(0.000001)]],
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(page = this.currentPage()): void {
    this.productsLoading.set(true);
    this.productService.findAll(page, this.pageSize).pipe(finalize(() => this.productsLoading.set(false))).subscribe({
      next: result => {
        this.products.set(result.content ?? []);
        this.currentPage.set(result.number ?? page);
        this.totalPages.set(result.totalPages ?? 0);
        this.totalElements.set(result.totalElements ?? 0);
      },
    });
  }

  selectProduct(product: Product): void {
    if (!product.id || !product.controlsStock) return;
    this.selectedProduct.set(product);
    this.operation.set(null);
    this.operationReference = null;
    this.form.reset();
    this.loadStock(product.id, 0);
  }

  loadStock(productId = this.selectedProduct()?.id, movementsPage = this.movementsPage()): void {
    if (!productId) return;
    this.stockLoading.set(true);
    forkJoin({
      balance: this.stockService.balance(productId),
      movements: this.stockService.movements(productId, movementsPage, 20),
    }).pipe(finalize(() => this.stockLoading.set(false))).subscribe({
      next: ({ balance, movements }) => {
        this.balance.set(balance);
        this.movements.set(movements.content ?? []);
        this.movementsPage.set(movements.number ?? movementsPage);
        this.movementsTotalPages.set(movements.totalPages ?? 0);
      },
    });
  }

  beginOperation(kind: StockOperationKind): void {
    if (kind === 'INITIAL' && !this.canInitialize()) return;
    if (kind !== 'INITIAL' && !this.canAdjust()) return;
    this.operation.set(kind);
    this.operationReference = this.newOperationReference();
    this.form.reset();
  }

  cancelOperation(): void {
    if (this.saving()) return;
    this.operation.set(null);
    this.operationReference = null;
    this.form.reset();
  }

  submitOperation(): void {
    const productId = this.selectedProduct()?.id;
    const kind = this.operation();
    if (!productId || !kind || this.form.invalid || !this.operationReference) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const request: StockOperationRequest = {
      quantity: Number(value.quantity),
      reason: value.reason!.trim(),
      operationReference: this.operationReference,
    };

    this.saving.set(true);
    const call = kind === 'INITIAL'
      ? this.stockService.initializeBalance(productId, request)
      : kind === 'POSITIVE'
        ? this.stockService.adjustPositive(productId, request)
        : this.stockService.adjustNegative(productId, request);

    call.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notification.success(this.operationSuccessMessage(kind));
        this.operation.set(null);
        this.operationReference = null;
        this.form.reset();
        this.loadStock(productId, 0);
      },
      // O interceptor global é responsável por apresentar o erro da API.
      // A referência é preservada para permitir retry idempotente da mesma operação.
      error: () => {},
    });
  }

  loadMovementsPage(page: number): void {
    if (page < 0 || page >= this.movementsTotalPages() || page === this.movementsPage()) return;
    const productId = this.selectedProduct()?.id;
    if (!productId) return;
    this.loadStock(productId, page);
  }

  movementLabel(type: StockMovementType): string {
    const labels: Record<StockMovementType, string> = {
      INITIAL_BALANCE: 'Saldo inicial',
      PURCHASE_ENTRY: 'Entrada por compra',
      SALE_OUT: 'Saída por venda',
      SALE_CANCELLATION_RETURN: 'Retorno por cancelamento',
      POSITIVE_ADJUSTMENT: 'Ajuste positivo',
      NEGATIVE_ADJUSTMENT: 'Ajuste negativo',
    };
    return labels[type];
  }

  movementIsEntry(type: StockMovementType): boolean {
    return type === 'INITIAL_BALANCE'
      || type === 'PURCHASE_ENTRY'
      || type === 'SALE_CANCELLATION_RETURN'
      || type === 'POSITIVE_ADJUSTMENT';
  }

  movementSignedQuantity(movement: StockMovement): number {
    return this.movementIsEntry(movement.movementType) ? movement.quantity : -movement.quantity;
  }

  operationTitle(): string {
    switch (this.operation()) {
      case 'INITIAL': return 'Informar saldo inicial';
      case 'POSITIVE': return 'Ajuste positivo';
      case 'NEGATIVE': return 'Ajuste negativo';
      default: return '';
    }
  }

  private operationSuccessMessage(kind: StockOperationKind): string {
    if (kind === 'INITIAL') return 'Saldo inicial registrado com sucesso.';
    return kind === 'POSITIVE' ? 'Ajuste positivo registrado com sucesso.' : 'Ajuste negativo registrado com sucesso.';
  }

  private newOperationReference(): string {
    return `stock-ui:${crypto.randomUUID()}`;
  }
}
