import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NotificationService } from '../../../core/services/notification-service';
import { ProductService } from '../../../core/services/product-service';
import { StockService } from '../../../core/services/stock-service';
import { StockOperationComponent } from './stock-operation';

describe('StockOperationComponent', () => {
  let fixture: ComponentFixture<StockOperationComponent>;
  let component: StockOperationComponent;
  let stock: { balance: ReturnType<typeof vi.fn>; movements: ReturnType<typeof vi.fn>; initializeBalance: ReturnType<typeof vi.fn>; adjustPositive: ReturnType<typeof vi.fn>; adjustNegative: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    stock = {
      balance: vi.fn().mockReturnValue(of({ productId: 1, productCode: 'P1', productName: 'Produto', unitOfMeasure: 'UN', controlsStock: true, initialized: false, quantity: null })),
      movements: vi.fn().mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })),
      initializeBalance: vi.fn().mockReturnValue(of({})),
      adjustPositive: vi.fn().mockReturnValue(of({})),
      adjustNegative: vi.fn().mockReturnValue(of({})),
    };

    await TestBed.configureTestingModule({
      imports: [StockOperationComponent],
      providers: [
        { provide: ProductService, useValue: { findAll: vi.fn().mockReturnValue(of({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 })) } },
        { provide: StockService, useValue: stock },
        { provide: NotificationService, useValue: { success: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StockOperationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('distinguishes uninitialized stock from zero balance', () => {
    component.selectProduct({ id: 1, code: 'P1', name: 'Produto', status: 'ATIVO', unitOfMeasure: 'UN', controlsStock: true, suppliers: [], images: [], active: true });
    expect(component.balance()?.initialized).toBe(false);
    expect(component.balance()?.quantity).toBeNull();
    expect(component.canInitialize()).toBe(true);
    expect(component.canAdjust()).toBe(false);
  });

  it('keeps the same idempotency reference for retry until operation succeeds', () => {
    component.selectProduct({ id: 1, code: 'P1', name: 'Produto', status: 'ATIVO', unitOfMeasure: 'UN', controlsStock: true, suppliers: [], images: [], active: true });
    component.beginOperation('INITIAL');
    component.form.setValue({ quantity: 10, reason: 'Contagem inicial' });
    component.submitOperation();
    expect(stock.initializeBalance).toHaveBeenCalledOnce();
    const request = stock.initializeBalance.mock.calls[0][1];
    expect(request.operationReference).toMatch(/^stock-ui:/);
  });
  it('presents outgoing movements with a negative sign without changing the persisted quantity', () => {
    const movement = { id: 1, productId: 1, movementType: 'SALE_OUT' as const, origin: 'SALE' as const, sourceReference: 'SALE_ITEM:1', quantity: 2, previousBalance: 10, resultingBalance: 8, saleId: 1, saleItemId: 1, reason: null, createdBy: 'user', createdAt: '2026-10-09T10:00:00' };
    expect(component.movementIsEntry(movement.movementType)).toBe(false);
    expect(component.movementSignedQuantity(movement)).toBe(-2);
  });

});
