import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ReportService } from '../../../core/services/report-service';
import { StockReport } from '../models/report';
import { StockReportComponent } from './stock-report';

describe('StockReportComponent', () => {
  let component: StockReportComponent;
  let fixture: ComponentFixture<StockReportComponent>;
  let reportService: { stock: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    reportService = {
      stock: vi.fn().mockReturnValue(of(stockReportFixture())),
    };

    await TestBed.configureTestingModule({
      imports: [StockReportComponent],
      providers: [{ provide: ReportService, useValue: reportService }],
    }).compileComponents();

    fixture = TestBed.createComponent(StockReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load the stock report without issuing a real HTTP request', () => {
    expect(component).toBeTruthy();
    expect(reportService.stock).toHaveBeenCalledTimes(1);
    expect(reportService.stock).toHaveBeenCalledWith(component.startDate, component.endDate);
    expect(component.report()).toEqual(stockReportFixture());
    expect(component.loading()).toBe(false);
  });
});

function stockReportFixture(): StockReport {
  return {
    startDate: '2026-10-01',
    endDate: '2026-10-09',
    products: 1,
    stockControlledProducts: 1,
    quantitySold: 3,
    salesValue: 45,
    items: [
      {
        productId: 1,
        code: 'P001',
        name: 'Produto teste',
        unitOfMeasure: 'UN',
        controlsStock: true,
        minimumStock: 2,
        maximumStock: 20,
        reorderPoint: 5,
        salePrice: 15,
        quantitySold: 3,
        salesValue: 45,
      },
    ],
  };
}
