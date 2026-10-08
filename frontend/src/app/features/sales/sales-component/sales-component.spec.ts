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
});
