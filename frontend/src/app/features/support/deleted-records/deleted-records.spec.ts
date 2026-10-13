import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DeletedRecordsComponent } from './deleted-records';

describe('DeletedRecordsComponent', () => {
  let component: DeletedRecordsComponent;
  let fixture: ComponentFixture<DeletedRecordsComponent>;
  let http: HttpTestingController;

  const page = (content: unknown[] = [], number = 0, totalElements = content.length, totalPages = totalElements ? 1 : 0) => ({
    content,
    page: number,
    size: 20,
    totalElements,
    totalPages,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeletedRecordsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(DeletedRecordsComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    const request = http.expectOne(req =>
      req.url === '/api/support/deleted-records' &&
      req.params.get('type') === 'CLIENT' &&
      req.params.get('page') === '0' &&
      req.params.get('size') === '20'
    );
    request.flush(page());
  });

  afterEach(() => http.verify());

  it('loads a paged response', () => {
    expect(component.currentPage).toBe(0);
    expect(component.totalElements).toBe(0);
    expect(component.totalPages).toBe(0);
  });

  it('resets to first page when searching', () => {
    component.currentPage = 3;
    component.search = 'produto';
    component.searchRecords();

    const request = http.expectOne(req =>
      req.url === '/api/support/deleted-records' &&
      req.params.get('search') === 'produto' &&
      req.params.get('page') === '0'
    );
    request.flush(page());

    expect(component.currentPage).toBe(0);
  });

  it('resets search and page when changing type', () => {
    component.currentPage = 2;
    component.search = 'abc';
    component.changeType('PRODUCT');

    const request = http.expectOne(req =>
      req.url === '/api/support/deleted-records' &&
      req.params.get('type') === 'PRODUCT' &&
      req.params.get('search') === '' &&
      req.params.get('page') === '0'
    );
    request.flush(page());

    expect(component.currentPage).toBe(0);
    expect(component.search).toBe('');
  });

  it('reloads the previous page after restoring the only record on a non-first page', () => {
    const record = { id: 10, type: 'PRODUCT' as const, title: 'Produto', subtitle: 'P10', lastModifiedAt: null };
    component.records = [record];
    component.currentPage = 1;
    component.totalPages = 2;
    component.totalElements = 21;
    component.confirmRestore(record);
    component.restore();

    http.expectOne('/api/support/deleted-records/PRODUCT/10/restore').flush(null);
    const reload = http.expectOne(req =>
      req.url === '/api/support/deleted-records' &&
      req.params.get('page') === '0'
    );
    reload.flush(page([], 0, 20, 1));

    expect(component.currentPage).toBe(0);
    expect(component.success).toBe('Registro recuperado com sucesso.');
  });
});
