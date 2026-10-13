import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserService } from './user-service';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('deve enviar paginação e pesquisa normalizada ao listar usuários', () => {
    service.findAll(2, 10, '  maria  ').subscribe();

    const req = http.expectOne((request) =>
      request.url === '/api/users' &&
      request.params.get('page') === '2' &&
      request.params.get('size') === '10' &&
      request.params.get('q') === 'maria'
    );

    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 2 });
  });

  it('não deve enviar q quando a pesquisa estiver vazia', () => {
    service.findAll(0, 10, '   ').subscribe();

    const req = http.expectOne('/api/users?page=0&size=10');
    expect(req.request.params.has('q')).toBe(false);
    req.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 0 });
  });
});
