import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BackupService } from './backup-service';

describe('BackupService', () => {
  let service: BackupService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BackupService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('deve listar os backups', () => {
    service.findAll().subscribe();
    const req = http.expectOne('/api/backups');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('deve solicitar a geração de um backup', () => {
    service.create().subscribe();
    const req = http.expectOne('/api/backups');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    req.flush({ fileName: 'backup.spbackup', sizeBytes: 100, createdAt: '2026-10-14T20:43:40Z' });
  });

  it('deve baixar o arquivo como blob usando o nome codificado', () => {
    service.download('systempro backup.spbackup').subscribe();
    const req = http.expectOne('/api/backups/systempro%20backup.spbackup');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['backup']));
  });
});
