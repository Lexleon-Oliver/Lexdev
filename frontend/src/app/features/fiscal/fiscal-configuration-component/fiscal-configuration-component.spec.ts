import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { FiscalConfigurationComponent } from './fiscal-configuration-component';

describe('FiscalConfigurationComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FiscalConfigurationComponent],
      providers: [provideHttpClient()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(FiscalConfigurationComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
