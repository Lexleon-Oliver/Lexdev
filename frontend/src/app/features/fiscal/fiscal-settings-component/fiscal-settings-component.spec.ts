import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FiscalSettingsComponent } from './fiscal-settings-component';

describe('FiscalSettingsComponent', () => {
  let component: FiscalSettingsComponent;
  let fixture: ComponentFixture<FiscalSettingsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FiscalSettingsComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(FiscalSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
