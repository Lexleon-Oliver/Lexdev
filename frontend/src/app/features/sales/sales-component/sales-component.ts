import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { Product } from '../../models/product';
import { PaymentMethod, Sale, SaleCreateRequest } from '../../models/sale';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../core/services/product-service';
import { FiscalEstablishmentService } from '../../../core/services/fiscal-establishment-service';
import { ClientService } from '../../../core/services/client-service';
import { NotificationService } from '../../../core/services/notification-service';
import { FiscalEstablishment } from '../../models/fiscal-establishment';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, finalize, map, of, Subject, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SaleService } from '../../../core/services/sale-service';

interface CartItem {
  product: Product;
  quantity: number;
  unitPrice: number;
  discount: number;
}

interface PaymentLine {
  paymentMethod: PaymentMethod;
  amount: number;
  cardBrand: string;
  authorizationCode: string;
}

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  selector: 'app-sales-component',
  styleUrl: './sales-component.scss',
  templateUrl: './sales-component.html',
})
export class SalesComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly fiscalEstablishmentService = inject(FiscalEstablishmentService);
  private readonly saleService = inject(SaleService);
  private readonly clientService = inject(ClientService);
  private readonly notification = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly productSearch = new Subject<string>();
  private autoSuggestedPayment = true;

  establishments = signal<FiscalEstablishment[]>([]);
  products = signal<Product[]>([]);
  recentSales = signal<Sale[]>([]);
  selectedEstablishmentId = signal<number | null>(null);
  cart = signal<CartItem[]>([]);
  payments = signal<PaymentLine[]>([
    { paymentMethod: 'DINHEIRO', amount: 0, cardBrand: '', authorizationCode: '' },
  ]);
  search = '';
  consumerCpfCnpj = '';
  clientName = '';
  clientId: number | null = null;
  saleDiscount = 0;
  note = '';
  isLoadingProducts = false;
  isSaving = signal(false);
  selectedSale = signal<Sale | null>(null);
  errorMessage = signal<string | null>(null);

  readonly paymentMethods: { value: PaymentMethod; label: string }[] = [
    { value: 'DINHEIRO', label: 'Dinheiro' },
    { value: 'PIX', label: 'PIX' },
    { value: 'CREDITO', label: 'Cartão de crédito' },
    { value: 'DEBITO', label: 'Cartão de débito' },
    { value: 'OUTRO', label: 'Outro' },
  ];

  ngOnInit(): void {
    this.configureProductSearch();
    this.loadFiscalEstablishments();
    this.loadInitialProducts();
    this.loadRecentSales();
  }

  loadInitialProducts(): void {
    this.isLoadingProducts = true;
    this.productService.findAll(0, 12)
      .pipe(finalize(() => this.isLoadingProducts = false))
      .subscribe({
        next: page => this.products.set(page.content ?? []),
        error: () => {
          this.products.set([]);
          this.notification.error('Não foi possível carregar os produtos.');
        },
      });
  }

  loadFiscalEstablishments(): void {
    this.fiscalEstablishmentService.findAll().subscribe({
      next: (items) => {
        this.establishments.set(items);
        if (!this.selectedEstablishmentId() && items.length > 0) {
          this.selectedEstablishmentId.set(items[0].id ?? null);
        }
      },
      error: () => this.errorMessage.set('Não foi possível carregar os estabelecimentos fiscais.'),
    });
  }

  private configureProductSearch(): void {
    this.productSearch.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(query => {
        this.isLoadingProducts = true;
        const request = query
          ? this.productService.searchForSale(query, 0, 12)
          : this.productService.findAll(0, 12);

        return request.pipe(
          catchError(() => {
            this.products.set([]);
            this.notification.error('Não foi possível pesquisar os produtos.');
            return EMPTY;
          }),
          finalize(() => this.isLoadingProducts = false),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(page => this.products.set(page.content ?? []));
  }

  onProductSearchChange(value: string): void {
    this.search = value;
    this.productSearch.next(value.trim());
  }

  searchProducts(): void {
    this.productSearch.next(this.search.trim());
  }

  addProduct(product: Product): void {
    if (!product.id) return;
    this.addToCart(product);
  }

  private addToCart(product: Product): void {
    const current = this.cart();
    const existing = current.find(item => item.product.id === product.id);
    if (existing) {
      this.cart.set(current.map(item => item.product.id === product.id
        ? { ...item, quantity: item.quantity + 1 }
        : item));
    } else {
      this.cart.set([
        ...current,
        {
          product,
          quantity: 1,
          unitPrice: Number(product.salePrice ?? 0),
          discount: 0,
        },
      ]);
    }
    this.products.set([]);
    this.search = '';
    this.recalculateSuggestedPayment();
  }

  updateCartItem(index: number, field: 'quantity' | 'unitPrice' | 'discount', value: number): void {
    const amount = Number.isFinite(value) ? Math.max(0, value) : 0;
    this.cart.set(this.cart().map((item, i) => i === index ? { ...item, [field]: amount } : item));
    this.recalculateSuggestedPayment();
  }

  removeCartItem(index: number): void {
    this.cart.set(this.cart().filter((_, i) => i !== index));
    this.recalculateSuggestedPayment();
  }

  addPayment(): void {
    this.autoSuggestedPayment = false;
    this.payments.update(items => [
      ...items,
      { paymentMethod: 'PIX', amount: 0, cardBrand: '', authorizationCode: '' },
    ]);
  }

  removePayment(index: number): void {
    if (this.payments().length === 1) return;
    this.autoSuggestedPayment = false;
    this.payments.update(items => items.filter((_, i) => i !== index));
  }

  setPayment(index: number, patch: Partial<PaymentLine>): void {
    this.payments.update(items =>
      items.map((item, i) => i === index ? { ...item, ...patch } : item)
    );

    if (this.autoSuggestedPayment && this.payments().length === 1) {
      this.recalculateSuggestedPayment();
    }
  }

  onSaleDiscountChange(value: number): void {
    this.saleDiscount = Number.isFinite(+value) ? Math.max(0, +value) : 0;
    this.recalculateSuggestedPayment();
  }

  quantityStep(unitOfMeasure: string): number {
    return unitOfMeasure.trim().toUpperCase() === 'UN' ? 1 : 0.001;
  }

  formatMoney(value: number | null | undefined): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(value ?? 0));
  }

  formatMoneyInput(value: number | null | undefined): string {
    return new Intl.NumberFormat('pt-BR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(Number(value ?? 0));
  }

  updateMoneyCartItem(index: number, field: 'unitPrice' | 'discount', value: string): void {
    this.updateCartItem(index, field, this.parseBrazilianDecimal(value));
  }

  updatePaymentAmount(index: number, value: string): void {
    this.autoSuggestedPayment = false;
    const amount = this.parseBrazilianDecimal(value);
    this.payments.update(items =>
      items.map((item, i) => i === index ? { ...item, amount } : item)
    );
  }

  updateSaleDiscount(value: string): void {
    this.onSaleDiscountChange(this.parseBrazilianDecimal(value));
  }

  private parseBrazilianDecimal(value: string): number {
    const normalized = value
      .trim()
      .replace(/\s/g, '')
      .replace(/^R\$/i, '')
      .replace(/\./g, '')
      .replace(',', '.');
    const parsed = Number(normalized);
    return Number.isFinite(parsed) ? Math.max(0, parsed) : 0;
  }

  itemTotal(item: CartItem): number {
    return Math.max(0, item.quantity * item.unitPrice - Math.min(item.discount, item.quantity * item.unitPrice));
  }

  subtotal(): number {
    return this.cart().reduce((sum, item) => sum + item.quantity * item.unitPrice, 0);
  }

  itemDiscounts(): number {
    return this.cart().reduce((sum, item) => sum + Math.min(item.discount, item.quantity * item.unitPrice), 0);
  }

  total(): number {
    return Math.max(0, this.subtotal() - this.itemDiscounts() - Math.max(0, Number(this.saleDiscount) || 0));
  }

  totalPaid(): number {
    return this.payments().reduce((sum, payment) => sum + Math.max(0, Number(payment.amount) || 0), 0);
  }

  change(): number {
    return Math.max(0, this.totalPaid() - this.total());
  }

  remaining(): number {
    return Math.max(0, this.total() - this.totalPaid());
  }

  canFinishSale(): boolean {
    return !this.isSaving()
      && this.selectedEstablishmentId() !== null
      && this.cart().length > 0
      && this.total() > 0
      && this.remaining() <= 0.0001;
  }

  findClient(): void {
    const document = this.onlyDigits(this.consumerCpfCnpj);
    if (document.length !== 11 && document.length !== 14) {
      this.clientId = null;
      this.clientName = '';
      return;
    }
    this.clientService.findByDocument(document).subscribe({
      next: client => {
        this.clientId = client.id ?? null;
        this.clientName = client.person.name;
      },
      error: () => {
        this.clientId = null;
        this.clientName = '';
      },
    });
  }

  finishSale(): void {
    this.errorMessage.set(null);
    const establishmentId = this.selectedEstablishmentId();
    if (!establishmentId) {
      this.errorMessage.set('Configure um estabelecimento fiscal antes de vender.');
      return;
    }
    if (this.cart().length === 0) {
      this.errorMessage.set('Adicione pelo menos um produto à venda.');
      return;
    }
    if (this.total() <= 0) {
      this.errorMessage.set('O total da venda precisa ser maior que zero.');
      return;
    }
    if (this.remaining() > 0.0001) {
      this.errorMessage.set('Informe pagamentos suficientes para fechar a venda.');
      return;
    }

    const payload: SaleCreateRequest = {
      fiscalEstablishmentId: establishmentId,
      clientId: this.clientId,
      consumerCpfCnpj: this.onlyDigits(this.consumerCpfCnpj) || null,
      items: this.cart().map(item => ({
        productId: item.product.id!,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        discount: item.discount,
      })),
      payments: this.payments().map(payment => ({
        paymentMethod: payment.paymentMethod,
        amount: payment.amount,
        cardBrand: payment.cardBrand || null,
        authorizationCode: payment.authorizationCode || null,
      })),
      discount: Math.max(0, Number(this.saleDiscount) || 0),
      note: this.note.trim() || null,
    };

    this.isSaving.set(true);
    this.saleService.create(payload)
      .pipe(
        switchMap(draft => this.saleService.issue(draft.id).pipe(
          map(sale => ({ sale, fiscalError: null as unknown })),
          catchError(error => of({ sale: draft, fiscalError: error })),
        )),
        finalize(() => this.isSaving.set(false)),
      )
      .subscribe({
        next: result => {
          const sale = result.sale;
          this.selectedSale.set(sale);
          this.loadRecentSales();
          this.clearSale();

          if (result.fiscalError) {
            const message = (result.fiscalError as any)?.error?.message ?? 'Não foi possível emitir a NFC-e.';
            this.notification.show(
              `Venda #${sale.id} registrada. NFC-e não emitida: ${message}`,
              'warning',
              8000,
            );
            return;
          }

          if (sale.status === 'FISCALIZADA') {
            this.notification.success(`Venda #${sale.id} autorizada pela SEFAZ/MG.`);
          } else {
            this.notification.show(
              `Venda #${sale.id} registrada, mas a situação fiscal é ${this.statusLabel(sale.status)}.`,
              'warning',
              6000,
            );
          }
        },
        error: error => {
          // Erro na criação comercial da venda: o interceptor global exibe uma única mensagem.
          console.error(error);
        },
      });
  }

  clearSale(): void {
    this.cart.set([]);
    this.consumerCpfCnpj = '';
    this.clientId = null;
    this.clientName = '';
    this.saleDiscount = 0;
    this.note = '';
    this.autoSuggestedPayment = true;
    this.payments.set([{ paymentMethod: 'DINHEIRO', amount: 0, cardBrand: '', authorizationCode: '' }]);
  }

  loadRecentSales(): void {
    this.saleService.findAll(0, 10).subscribe({
      next: page => this.recentSales.set(page.content ?? []),
      error: () => this.recentSales.set([]),
    });
  }

  consult(sale: Sale): void {
    this.saleService.consult(sale.id).subscribe({
      next: updated => {
        this.selectedSale.set(updated);
        this.loadRecentSales();
      },
      error: () => this.notification.error('Não foi possível consultar a situação fiscal.'),
    });
  }

  cancel(sale: Sale): void {
    if (sale.status !== 'FISCALIZADA' || !sale.fiscalDocument?.accessKey) return;
    const justification = window.prompt('Justificativa do cancelamento (mínimo de 15 caracteres):', 'Cancelamento solicitado pelo estabelecimento');
    if (!justification) return;
    this.saleService.cancel(sale.id, justification).subscribe({
      next: updated => {
        this.selectedSale.set(updated);
        this.loadRecentSales();
        this.notification.success('Solicitação de cancelamento processada.');
      },
      error: error => this.notification.error(error?.error?.message ?? 'Não foi possível cancelar a NFC-e.'),
    });
  }

  downloadXml(sale: Sale): void {
    if (!sale.fiscalDocument?.accessKey) return;
    this.saleService.downloadXml(sale.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `nfce-venda-${sale.id}.xml`;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.notification.error('XML fiscal indisponível.'),
    });
  }

  statusLabel(status: string | undefined): string {
    switch (status) {
      case 'FISCALIZADA': return 'Fiscalizada';
      case 'FISCAL_PENDENTE': return 'Pendente de consulta';
      case 'FISCAL_REJEITADA': return 'Rejeitada';
      case 'CANCELADA': return 'Cancelada';
      default: return 'Aguardando fiscal';
    }
  }

  fiscalStatusLabel(status: string | undefined): string {
    const labels: Record<string, string> = {
      AUTORIZADA: 'Autorizada',
      REJEITADA: 'Rejeitada',
      PENDENTE_CONSULTA: 'Pendente de consulta',
      CANCELADA: 'Cancelada',
      CANCELAMENTO_PENDENTE: 'Cancelamento pendente',
      AGUARDANDO_AUTORIZACAO: 'Aguardando autorização',
      CONTINGENCIA: 'Contingência',
    };
    return labels[status ?? ''] ?? status ?? '—';
  }

  private onlyDigits(value: string): string {
    return (value ?? '').replace(/\D/g, '');
  }

  private recalculateSuggestedPayment(): void {
    if (this.autoSuggestedPayment && this.payments().length === 1) {
      this.payments.update(items =>
        items.map((item, index) => index === 0 ? { ...item, amount: this.total() } : item)
      );
    }
  }
}
