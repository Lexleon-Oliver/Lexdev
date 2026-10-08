import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SalesComponent } from './sales-component';

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

});
