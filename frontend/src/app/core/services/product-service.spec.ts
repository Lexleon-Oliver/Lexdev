import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductService } from './product-service';

describe('ProductService', () => {
  let service: ProductService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProductService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('deve enviar paginação e pesquisa normalizada ao listar produtos', () => {
    service.findAll(2, 10, '  motor  ').subscribe();

    const req = http.expectOne((request) =>
      request.url === '/api/products' &&
      request.params.get('page') === '2' &&
      request.params.get('size') === '10' &&
      request.params.get('q') === 'motor'
    );

    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0 });
  });

  it('não deve enviar q quando a pesquisa estiver vazia', () => {
    service.findAll(0, 10, '   ').subscribe();

    const req = http.expectOne('/api/products?page=0&size=10');
    expect(req.request.params.has('q')).toBe(false);
    req.flush({ content: [], totalElements: 0 });
  });
  it('deve enviar o filtro de controle de estoque quando informado', () => {
    service.findAll(0, 20, ' leite ', true).subscribe();

    const req = http.expectOne((request) =>
      request.url === '/api/products' &&
      request.params.get('q') === 'leite' &&
      request.params.get('controlsStock') === 'true'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 });
  });

});
