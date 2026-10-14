import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BackupComponent } from './backup-component';

describe('BackupComponent', () => {
  let component: BackupComponent;
  let fixture: ComponentFixture<BackupComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BackupComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(BackupComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('deve carregar e ordenar os backups do mais recente para o mais antigo', () => {
    http.expectOne('/api/backups').flush([
      { fileName: 'old.spbackup', sizeBytes: 100, createdAt: '2026-10-14T10:00:00Z' },
      { fileName: 'new.spbackup', sizeBytes: 200, createdAt: '2026-10-14T20:00:00Z' },
    ]);

    expect(component.backups().map(item => item.fileName)).toEqual([
      'new.spbackup',
      'old.spbackup',
    ]);
  });

  it('deve incluir o backup recém-gerado na lista', () => {
    http.expectOne('/api/backups').flush([]);

    component.createBackup();
    const request = http.expectOne('/api/backups');
    expect(request.request.method).toBe('POST');
    request.flush({
      fileName: 'systempro-backup.spbackup',
      sizeBytes: 4635508,
      createdAt: '2026-10-14T20:43:40Z',
    });

    expect(component.backups()[0].fileName).toBe('systempro-backup.spbackup');
    expect(component.isCreating()).toBe(false);
  });

  it('deve formatar tamanho em unidades legíveis', () => {
    http.expectOne('/api/backups').flush([]);
    expect(component.formatSize(4635508)).toContain('MB');
  });
});
