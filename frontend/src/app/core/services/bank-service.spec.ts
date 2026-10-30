import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BankService } from './bank-service';

describe('BankService', () => {
  let service: BankService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(BankService);
    http = TestBed.inject(HttpTestingController);
  });

  it('normaliza, filtra e ordena os bancos da BrasilAPI', () => {
    let result: any[] = [];
    service.getBanks().subscribe((r) => (result = r));

    http.expectOne('https://brasilapi.com.br/api/banks/v1').flush([
      { ispb: '2', name: 'ITAÚ UNIBANCO S.A.', code: 341, fullName: 'Itaú Unibanco S.A.' },
      { ispb: '1', name: 'BCO DO BRASIL S.A.', code: 1, fullName: 'Banco do Brasil S.A.' },
      { ispb: '3', name: 'SEM CODIGO', code: null, fullName: 'Sem código' }
    ]);

    expect(result.map((b) => b.label)).toEqual([
      '001 - BCO DO BRASIL S.A.',
      '341 - ITAÚ UNIBANCO S.A.'
    ]);
  });

  it('usa a lista de reserva quando a API falha', () => {
    let result: any[] = [];
    service.getBanks().subscribe((r) => (result = r));

    http.expectOne('https://brasilapi.com.br/api/banks/v1').error(new ProgressEvent('error'));

    expect(result.length).toBeGreaterThan(0);
    expect(result.some((b) => b.code === '341')).toBe(true);
  });

  it('reaproveita a lista já carregada', () => {
    service.getBanks().subscribe();
    http.expectOne('https://brasilapi.com.br/api/banks/v1').flush([]);
    service.getBanks().subscribe();
    http.expectNone('https://brasilapi.com.br/api/banks/v1');
  });
});
