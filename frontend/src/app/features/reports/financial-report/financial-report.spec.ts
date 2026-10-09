import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FinancialReport } from './financial-report';

describe('FinancialReport', () => {
  let component: FinancialReport;
  let fixture: ComponentFixture<FinancialReport>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FinancialReport]
    })
      .compileComponents();

    fixture = TestBed.createComponent(FinancialReport);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
