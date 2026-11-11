import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SupportAiConfigurationComponent } from './ai-configuration';

const url = '/api/support/configuration/ai-output-tokens';

describe('SupportAiConfigurationComponent', () => {
  let fixture: ComponentFixture<SupportAiConfigurationComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupportAiConfigurationComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(SupportAiConfigurationComponent);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('consulta um parâmetro permitido sem obter credenciais', () => {
    fixture.detectChanges();
    const req = http.expectOne(url);
    expect(req.request.method).toBe('GET');
    req.flush({ effectiveValue: 600, installationDefault: 600, overridden: false });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Configuração da instalação');
    expect(fixture.componentInstance.form.controls.value.value).toBe(600);
  });

  it('salva somente um inteiro válido e permite restaurar padrão', () => {
    fixture.detectChanges();
    http.expectOne(url).flush({ effectiveValue: 600, installationDefault: 600, overridden: false });
    fixture.detectChanges();
    const c = fixture.componentInstance;
    c.form.controls.value.setValue(900);
    c.save();
    const save = http.expectOne(url);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body).toEqual({ value: 900 });
    save.flush({ effectiveValue: 900, installationDefault: 600, overridden: true });
    fixture.detectChanges();
    c.reset();
    const reset = http.expectOne(url);
    expect(reset.request.method).toBe('DELETE');
    reset.flush({ effectiveValue: 600, installationDefault: 600, overridden: false });
    fixture.detectChanges();
    expect(c.settings?.overridden).toBe(false);
  });

  it('bloqueia valor inválido e não faz PUT', () => {
    fixture.detectChanges();
    http.expectOne(url).flush({ effectiveValue: 600, installationDefault: 600, overridden: false });
    fixture.detectChanges();
    const c = fixture.componentInstance;
    c.form.controls.value.setValue(9000);
    c.save();
    http.expectNone(url);
    expect(c.form.invalid).toBe(true);
  });
});
