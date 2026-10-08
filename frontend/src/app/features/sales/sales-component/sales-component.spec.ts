import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SalesComponent } from './sales-component';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Sale } from '../../models/sale';


function fiscalSale(
  saleStatus: Sale['status'],
  fiscalStatus: NonNullable<Sale['fiscalDocument']>['status'],
  emissionType: NonNullable<Sale['fiscalDocument']>['emissionType'],
  accessKey: string | null = null,
): Sale {
  return {
    id: 99,
    fiscalEstablishmentId: 1,
    userId: 1,
    status: saleStatus,
    saleAt: '2026-10-08T15:00:00',
    subtotal: 10,
    discount: 0,
    total: 10,
    totalPaid: 10,
    change: 0,
    items: [],
    payments: [],
    fiscalDocument: {
      id: 99,
      model: '65',
      series: 1,
      number: 1,
      status: fiscalStatus,
      emissionType,
      accessKey,
    },
  };
}

describe('SalesComponent', () => {
  let component: SalesComponent;
  let fixture: ComponentFixture<SalesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SalesComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(SalesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should recalculate totals and suggested cash payment when an item changes', () => {
    component.cart.set([{
      product: {
        id: 1,
        code: 'P001',
        name: 'Produto teste',
        status: 'ATIVO',
        unitOfMeasure: 'UN',
        controlsStock: true,
        salePrice: 10,
        suppliers: [],
        images: [],
        active: true,
      },
      quantity: 1,
      unitPrice: 10,
      discount: 0,
    }]);

    component.updateCartItem(0, 'quantity', 2);

    expect(component.total()).toBe(20);
    expect(component.payments()[0].amount).toBe(20);
  });

  it('should recalculate total and suggested cash payment when sale discount changes', () => {
    component.cart.set([{
      product: {
        id: 1,
        code: 'P001',
        name: 'Produto teste',
        status: 'ATIVO',
        unitOfMeasure: 'UN',
        controlsStock: true,
        salePrice: 100,
        suppliers: [],
        images: [],
        active: true,
      },
      quantity: 1,
      unitPrice: 100,
      discount: 0,
    }]);

    component.onSaleDiscountChange(15);

    expect(component.total()).toBe(85);
    expect(component.payments()[0].amount).toBe(85);
  });

  it('should use integer increments for unit products', () => {
    expect(component.quantityStep('UN')).toBe(1);
  });

  it('should format monetary values using pt-BR conventions', () => {
    expect(component.formatMoney(3499.9)).toContain('3.499,90');
    expect(component.formatMoneyInput(3499.9)).toBe('3.499,90');
  });

  it('should parse pt-BR monetary input without changing the numeric value', () => {
    component.cart.set([{
      product: {
        id: 1,
        code: 'P001',
        name: 'Produto teste',
        status: 'ATIVO',
        unitOfMeasure: 'UN',
        controlsStock: true,
        salePrice: 3499.9,
        suppliers: [],
        images: [],
        active: true,
      },
      quantity: 1,
      unitPrice: 3499.9,
      discount: 0,
    }]);

    component.updateMoneyCartItem(0, 'unitPrice', '3.499,90');

    expect(component.cart()[0].unitPrice).toBe(3499.9);
    expect(component.total()).toBe(3499.9);
  });


  it('should keep automatic payment synchronized after changing the single payment method', () => {
    component.cart.set([{
      product: {
        id: 1, code: 'P001', name: 'Produto teste', status: 'ATIVO',
        unitOfMeasure: 'UN', controlsStock: true, salePrice: 100,
        suppliers: [], images: [], active: true,
      },
      quantity: 1, unitPrice: 100, discount: 0,
    }]);

    component.updateCartItem(0, 'quantity', 1);
    component.setPayment(0, { paymentMethod: 'PIX' });
    component.updateCartItem(0, 'quantity', 2);

    expect(component.payments()[0].paymentMethod).toBe('PIX');
    expect(component.payments()[0].amount).toBe(200);
    expect(component.remaining()).toBe(0);
  });

  it('should preserve a manually entered payment when the cart total changes', () => {
    component.cart.set([{
      product: {
        id: 1, code: 'P001', name: 'Produto teste', status: 'ATIVO',
        unitOfMeasure: 'UN', controlsStock: true, salePrice: 100,
        suppliers: [], images: [], active: true,
      },
      quantity: 1, unitPrice: 100, discount: 0,
    }]);

    component.updateCartItem(0, 'quantity', 1);
    component.updatePaymentAmount(0, '150,00');
    component.updateCartItem(0, 'quantity', 2);

    expect(component.payments()[0].amount).toBe(150);
    expect(component.remaining()).toBe(50);
  });

  it('should only allow finishing with establishment, items, positive total and sufficient payment', () => {
    component.cart.set([{
      product: {
        id: 1, code: 'P001', name: 'Produto teste', status: 'ATIVO',
        unitOfMeasure: 'UN', controlsStock: true, salePrice: 100,
        suppliers: [], images: [], active: true,
      },
      quantity: 1, unitPrice: 100, discount: 0,
    }]);

    component.updateCartItem(0, 'quantity', 1);

    component.selectedEstablishmentId.set(null);
    expect(component.canFinishSale()).toBe(false);

    component.selectedEstablishmentId.set(1);
    expect(component.canFinishSale()).toBe(true);

    component.updatePaymentAmount(0, '50,00');
    expect(component.canFinishSale()).toBe(false);
  });


  it('should never start offline contingency automatically when normal issue fails', () => {
    const draft = fiscalSale('AGUARDANDO_FISCAL', 'AGUARDANDO_AUTORIZACAO', 'NORMAL');
    const saleService = (component as any).saleService;
    const createSpy = vi.spyOn(saleService, 'create').mockReturnValue(of(draft));
    const issueSpy = vi.spyOn(saleService, 'issue').mockReturnValue(
      throwError(() => ({ error: { message: 'O certificado A1 ainda não foi configurado.' } })),
    );
    const contingencySpy = vi.spyOn(saleService, 'prepareOfflineContingency');
    vi.spyOn(component, 'loadRecentSales').mockImplementation(() => undefined);

    component.selectedEstablishmentId.set(1);
    component.cart.set([{
      product: {
        id: 1, code: 'P001', name: 'Produto teste', status: 'ATIVO',
        unitOfMeasure: 'UN', controlsStock: true, salePrice: 10,
        suppliers: [], images: [], active: true,
      },
      quantity: 1, unitPrice: 10, discount: 0,
    }]);
    component.payments.set([{
      paymentMethod: 'DINHEIRO', amount: 10, cardBrand: '', authorizationCode: '',
    }]);

    component.finishSale();

    expect(createSpy).toHaveBeenCalledOnce();
    expect(issueSpy).toHaveBeenCalledWith(draft.id);
    expect(contingencySpy).not.toHaveBeenCalled();
    expect(component.selectedSale()).toEqual(draft);
    expect(component.isSaving()).toBe(false);
  });

  it('should refuse invalid offline justification without calling the backend', () => {
    const sale = fiscalSale('AGUARDANDO_FISCAL', 'AGUARDANDO_AUTORIZACAO', 'NORMAL');
    const saleService = (component as any).saleService;
    const contingencySpy = vi.spyOn(saleService, 'prepareOfflineContingency');

    component.openOfflineContingency(sale);
    component.contingencyJustification = 'curta';
    component.confirmOfflineContingency();

    expect(contingencySpy).not.toHaveBeenCalled();
    expect(component.contingencySale()).toEqual(sale);
  });

  it('should prepare offline contingency only after explicit confirmation and apply returned state', () => {
    const sale = fiscalSale('AGUARDANDO_FISCAL', 'AGUARDANDO_AUTORIZACAO', 'NORMAL');
    const prepared = fiscalSale('FISCAL_PENDENTE', 'CONTINGENCIA', 'CONTINGENCIA_OFFLINE', 'KEY-OFFLINE');
    const saleService = (component as any).saleService;
    const contingencySpy = vi.spyOn(saleService, 'prepareOfflineContingency').mockReturnValue(of(prepared));
    component.recentSales.set([sale]);

    component.openOfflineContingency(sale);
    component.contingencyJustification = 'Indisponibilidade conhecida da SEFAZ';
    component.confirmOfflineContingency();

    expect(contingencySpy).toHaveBeenCalledWith(sale.id, 'Indisponibilidade conhecida da SEFAZ');
    expect(component.selectedSale()).toEqual(prepared);
    expect(component.recentSales()[0]).toEqual(prepared);
    expect(component.contingencySale()).toBeNull();
    expect(component.fiscalActionSaleId()).toBeNull();
  });

  it('should transmit prepared contingency but never retransmit a pending-consultation document', () => {
    const prepared = fiscalSale('FISCAL_PENDENTE', 'CONTINGENCIA', 'CONTINGENCIA_OFFLINE', 'KEY-OFFLINE');
    const pending = fiscalSale('FISCAL_PENDENTE', 'PENDENTE_CONSULTA', 'CONTINGENCIA_OFFLINE', 'KEY-OFFLINE');
    const saleService = (component as any).saleService;
    const transmitSpy = vi.spyOn(saleService, 'transmitOfflineContingency').mockReturnValue(of(pending));

    component.transmitOfflineContingency(prepared);
    expect(transmitSpy).toHaveBeenCalledOnce();
    expect(component.selectedSale()).toEqual(pending);

    component.transmitOfflineContingency(pending);
    expect(transmitSpy).toHaveBeenCalledOnce();
  });

  it('should consult uncertain offline transmission instead of retransmitting it', () => {
    const pending = fiscalSale('FISCAL_PENDENTE', 'PENDENTE_CONSULTA', 'CONTINGENCIA_OFFLINE', 'KEY-OFFLINE');
    const authorized = fiscalSale('FISCALIZADA', 'AUTORIZADA', 'CONTINGENCIA_OFFLINE', 'KEY-OFFLINE');
    const saleService = (component as any).saleService;
    const transmitSpy = vi.spyOn(saleService, 'transmitOfflineContingency');
    const consultSpy = vi.spyOn(saleService, 'consult').mockReturnValue(of(authorized));

    component.consult(pending);

    expect(consultSpy).toHaveBeenCalledWith(pending.id);
    expect(transmitSpy).not.toHaveBeenCalled();
    expect(component.selectedSale()).toEqual(authorized);
  });


  it('should recover pending cancellation only through the dedicated endpoint', () => {
    const pendingCancellation = fiscalSale('FISCAL_PENDENTE', 'CANCELAMENTO_PENDENTE', 'NORMAL', 'KEY-AUTH');
    const cancelled = fiscalSale('CANCELADA', 'CANCELADA', 'NORMAL', 'KEY-AUTH');
    const saleService = (component as any).saleService;
    const normalConsultSpy = vi.spyOn(saleService, 'consult');
    const cancelSpy = vi.spyOn(saleService, 'cancel');
    const cancellationConsultSpy = vi.spyOn(saleService, 'consultPendingCancellation').mockReturnValue(of(cancelled));

    component.consultPendingCancellation(pendingCancellation);

    expect(cancellationConsultSpy).toHaveBeenCalledWith(pendingCancellation.id);
    expect(normalConsultSpy).not.toHaveBeenCalled();
    expect(cancelSpy).not.toHaveBeenCalled();
    expect(component.selectedSale()).toEqual(cancelled);
    expect(component.fiscalActionSaleId()).toBeNull();
  });

  it('should not use pending-cancellation recovery for ordinary pending consultation', () => {
    const pending = fiscalSale('FISCAL_PENDENTE', 'PENDENTE_CONSULTA', 'NORMAL', 'KEY-PENDING');
    const saleService = (component as any).saleService;
    const cancellationConsultSpy = vi.spyOn(saleService, 'consultPendingCancellation');

    component.consultPendingCancellation(pending);

    expect(cancellationConsultSpy).not.toHaveBeenCalled();
  });

  it('should load the fiscal pending queue from the dedicated paginated endpoint', () => {
    const pending = fiscalSale('FISCAL_PENDENTE', 'PENDENTE_CONSULTA', 'NORMAL', 'KEY-PENDING');
    const saleService = (component as any).saleService;
    const pendingSpy = vi.spyOn(saleService, 'findFiscalPending').mockReturnValue(of({
      content: [pending],
      totalElements: 21,
      totalPages: 2,
      size: 20,
      number: 0,
    }));

    component.loadFiscalPendingSales(0);

    expect(pendingSpy).toHaveBeenCalledWith(0, 20);
    expect(component.fiscalPendingSales()).toEqual([pending]);
    expect(component.fiscalPendingPage()).toBe(0);
    expect(component.fiscalPendingTotalPages()).toBe(2);
    expect(component.isLoadingFiscalPending()).toBe(false);
  });

  it('should page through fiscal pending sales without changing the recent-sales list', () => {
    const recent = fiscalSale('FISCALIZADA', 'AUTORIZADA', 'NORMAL', 'KEY-AUTH');
    const pending = fiscalSale('FISCAL_PENDENTE', 'CONTINGENCIA', 'CONTINGENCIA_OFFLINE', 'KEY-OFFLINE');
    const saleService = (component as any).saleService;
    const pendingSpy = vi.spyOn(saleService, 'findFiscalPending').mockReturnValue(of({
      content: [pending],
      totalElements: 21,
      totalPages: 2,
      size: 20,
      number: 1,
    }));
    component.recentSales.set([recent]);
    component.fiscalPendingPage.set(0);
    component.fiscalPendingTotalPages.set(2);

    component.nextFiscalPendingPage();

    expect(pendingSpy).toHaveBeenCalledWith(1, 20);
    expect(component.fiscalPendingSales()).toEqual([pending]);
    expect(component.fiscalPendingPage()).toBe(1);
    expect(component.recentSales()).toEqual([recent]);
  });


});
