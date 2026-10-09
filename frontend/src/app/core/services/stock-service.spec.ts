import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { StockService } from './stock-service';


describe('StockService', () => {
  let service: StockService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(StockService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads balance', () => {
    service.balance(7).subscribe();
    const req = http.expectOne('/api/stock/products/7');
    expect(req.request.method).toBe('GET');
    req.flush({ productId: 7, productCode: 'P7', productName: 'Produto', unitOfMeasure: 'UN', controlsStock: true, initialized: false, quantity: null });
  });

  it('sends positive adjustment preserving operation reference', () => {
    const body = { quantity: 3, reason: 'Contagem', operationReference: 'stock-ui:abc' };
    service.adjustPositive(7, body).subscribe();
    const req = http.expectOne('/api/stock/products/7/adjustments/positive');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });
});
