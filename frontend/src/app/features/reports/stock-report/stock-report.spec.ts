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
    reportService = { stock: vi.fn().mockReturnValue(of(stockReportFixture())) };
    await TestBed.configureTestingModule({ imports: [StockReportComponent], providers: [{ provide: ReportService, useValue: reportService }] }).compileComponents();
    fixture = TestBed.createComponent(StockReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads the stock report without issuing a real HTTP request', () => {
    expect(reportService.stock).toHaveBeenCalledOnce();
    expect(component.report()).toEqual(stockReportFixture());
    expect(component.loading()).toBe(false);
  });

  it('maps all stock statuses to presentation labels', () => {
    expect(component.stockStatusLabel('NOT_CONTROLLED')).toBe('Sem controle');
    expect(component.stockStatusLabel('NOT_INITIALIZED')).toBe('Não inicializado');
    expect(component.stockStatusLabel('OUT_OF_STOCK')).toBe('Sem estoque');
    expect(component.stockStatusLabel('BELOW_MINIMUM')).toBe('Abaixo do mínimo');
    expect(component.stockStatusLabel('REORDER')).toBe('Ponto de reposição');
    expect(component.stockStatusLabel('NORMAL')).toBe('Normal');
    expect(component.stockStatusLabel('ABOVE_MAXIMUM')).toBe('Acima do máximo');
  });
});

function stockReportFixture(): StockReport {
  return {
    startDate:'2026-10-01', endDate:'2026-10-09', products:1, stockControlledProducts:1,
    initializedStockProducts:1, replenishmentNeededProducts:1, currentBalance:4,
    stockEntries:10, stockOutputs:6, quantitySold:6, salesValue:90,
    items:[{ productId:1, code:'P001', name:'Produto teste', unitOfMeasure:'UN', controlsStock:true,
      initialized:true, currentBalance:4, stockStatus:'REORDER', replenishmentNeeded:true,
      minimumStock:2, maximumStock:20, reorderPoint:5, salePrice:15,
      stockEntries:10, stockOutputs:6, quantitySold:6, salesValue:90 }]
  };
}
