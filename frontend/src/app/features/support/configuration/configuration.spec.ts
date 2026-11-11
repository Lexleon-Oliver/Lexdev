import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SupportConfigurationComponent } from './configuration';

const overview = {
  features: [{
    key: 'systempro.fiscal.schema.emit-liquid-values', label: 'Emissão de valores líquidos',
    category: 'Fiscal experimental', enabled: false, requiresRestart: true,
  }],
  areas: [{ name: 'Segredos e licenciamento', ownership: 'INSTALADOR',
    location: 'Ambiente protegido', explanation: 'Segredos não são retornados.' }],
} as const;

describe('SupportConfigurationComponent', () => {
  let fixture: ComponentFixture<SupportConfigurationComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupportConfigurationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(SupportConfigurationComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta exclusivamente endpoint de suporte e nao permite edicao', () => {
    fixture.detectChanges();
    const req = http.expectOne('/api/support/configuration');
    expect(req.request.method).toBe('GET');
    req.flush(overview);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Desativado');
    expect(text).toContain('somente leitura');
    expect(fixture.nativeElement.querySelectorAll('input, select, textarea').length).toBe(0);
  });

  it('exibe erro sem deixar dados anteriores visiveis', () => {
    fixture.detectChanges();
    http.expectOne('/api/support/configuration').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(fixture.componentInstance.overview).toBeNull();
    expect(fixture.componentInstance.error).toContain('Não foi possível');
  });
});
