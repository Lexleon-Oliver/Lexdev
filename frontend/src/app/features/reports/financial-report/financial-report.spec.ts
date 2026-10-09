import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ReportService } from '../../../core/services/report-service';
import { FinancialReport } from '../models/report';
import { FinancialReportComponent } from './financial-report';

describe('FinancialReportComponent', () => {
  let component: FinancialReportComponent;
  let fixture: ComponentFixture<FinancialReportComponent>;
  let reportService: { financial: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    reportService = {
      financial: vi.fn().mockReturnValue(of(financialReportFixture())),
    };

    await TestBed.configureTestingModule({
      imports: [FinancialReportComponent],
      providers: [{ provide: ReportService, useValue: reportService }],
    }).compileComponents();

    fixture = TestBed.createComponent(FinancialReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load the financial report without issuing a real HTTP request', () => {
    expect(component).toBeTruthy();
    expect(reportService.financial).toHaveBeenCalledTimes(1);
    expect(reportService.financial).toHaveBeenCalledWith(component.startDate, component.endDate);
    expect(component.report()).toEqual(financialReportFixture());
    expect(component.loading()).toBe(false);
  });
});

function financialReportFixture(): FinancialReport {
  return {
    startDate: '2026-10-01',
    endDate: '2026-10-09',
    sales: 2,
    cancelledSales: 0,
    grossSales: 150,
    discounts: 10,
    netSales: 140,
    averageTicket: 70,
    payments: [{ paymentMethod: 'PIX', amount: 140 }],
    daily: [{ date: '2026-10-09', sales: 2, total: 140 }],
  };
}
