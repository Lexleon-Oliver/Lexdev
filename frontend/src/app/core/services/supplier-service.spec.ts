import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SupplierService } from './supplier-service';

describe('SupplierService', () => {
  let service: SupplierService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SupplierService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should request a paged supplier list without q when search is empty', () => {
    service.findAll(1, 10).subscribe();

    const request = http.expectOne(req =>
      req.url === '/api/suppliers' &&
      req.params.get('page') === '1' &&
      req.params.get('size') === '10' &&
      !req.params.has('q')
    );

    expect(request.request.method).toBe('GET');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 1 });
  });

  it('should send the trimmed server-side search term', () => {
    service.findAll(0, 10, '  Tech  ').subscribe();

    const request = http.expectOne(req =>
      req.url === '/api/suppliers' &&
      req.params.get('q') === 'Tech'
    );

    expect(request.request.method).toBe('GET');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });
});
