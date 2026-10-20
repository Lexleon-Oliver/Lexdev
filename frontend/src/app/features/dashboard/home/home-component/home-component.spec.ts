import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../../core/services/auth-service';
import { DashboardService } from '../../../../core/services/dashboard-service';
import { NotificationService } from '../../../../core/services/notification-service';
import { HomeComponent } from './home-component';
import { Dashboard } from '../../../models/dashboard';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;
  let dashboardService: {
    load: ReturnType<typeof vi.fn>;
    loadInsight: ReturnType<typeof vi.fn>;
  };
  let notificationService: { error: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    dashboardService = {
      load: vi.fn().mockReturnValue(of(dashboardFixture())),
      loadInsight: vi.fn().mockReturnValue(of({
        status: 'GENERATED',
        insight: 'As vendas permaneceram estáveis.',
      })),
    };
    notificationService = { error: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: DashboardService, useValue: dashboardService },
        { provide: NotificationService, useValue: notificationService },
        { provide: AuthService, useValue: { currentUser: () => ({ username: 'admin' }) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
  });

  it('should load dashboard on init', () => {
    fixture.detectChanges();

    expect(dashboardService.load).toHaveBeenCalledTimes(1);
    expect(dashboardService.loadInsight).toHaveBeenCalledTimes(1);
    expect(component.dashboard()).toEqual(dashboardFixture());
    expect(component.insight()).toBe('As vendas permaneceram estáveis.');
    expect(component.loading()).toBe(false);
    expect(component.insightLoading()).toBe(false);
    expect(component.hasFiscalPending()).toBe(true);
  });

  it('should render only a generated AI insight', () => {
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.insight-card') as HTMLElement | null;

    expect(card).not.toBeNull();
    expect(card?.textContent).toContain('Insight inteligente');
    expect(card?.textContent).toContain('As vendas permaneceram estáveis.');
    expect(card?.textContent).toContain('Gerado por IA');
  });

  it('should hide disabled and unavailable AI responses', () => {
    dashboardService.loadInsight.mockReturnValue(of({
      status: 'DISABLED',
      insight: 'IA desativada.',
    }));

    fixture.detectChanges();

    expect(component.insight()).toBeNull();
    expect(fixture.nativeElement.querySelector('.insight-card')).toBeNull();

    dashboardService.loadInsight.mockReturnValue(of({
      status: 'UNAVAILABLE',
      insight: 'IA indisponível.',
    }));

    component.refresh();
    fixture.detectChanges();

    expect(component.insight()).toBeNull();
    expect(fixture.nativeElement.querySelector('.insight-card')).toBeNull();
  });

  it('should keep the dashboard available when loading the AI insight fails', () => {
    dashboardService.loadInsight.mockReturnValue(
      throwError(() => new Error('provider unavailable'))
    );

    fixture.detectChanges();

    expect(component.dashboard()).toEqual(dashboardFixture());
    expect(component.insight()).toBeNull();
    expect(component.error()).toBeNull();
    expect(notificationService.error).not.toHaveBeenCalled();
  });

  it('should expose presentation helpers without recalculating business values', () => {
    expect(component.paymentLabel('PIX')).toBe('PIX');
    expect(component.saleStatusLabel('FISCAL_PENDENTE')).toBe('Fiscal pendente');
    expect(component.fiscalStatusLabel('CANCELAMENTO_PENDENTE')).toBe('Cancelamento pendente');
    expect(component.fiscalStatusLabel(null)).toBe('Sem documento fiscal');
    expect(component.maxDailyTotal(dashboardFixture().dailyPerformance)).toBe(200);
    expect(component.barWidth(100, 200)).toBe(50);
    expect(component.barWidth(100, 0)).toBe(0);
  });

  it('should expose an error and notify when loading fails', () => {
    dashboardService.load.mockReturnValue(throwError(() => new Error('backend unavailable')));

    component.load();

    expect(component.dashboard()).toBeNull();
    expect(component.error()).toBe('Não foi possível carregar o dashboard.');
    expect(notificationService.error).toHaveBeenCalledWith('Não foi possível carregar o dashboard.');
    expect(dashboardService.loadInsight).not.toHaveBeenCalled();
    expect(component.loading()).toBe(false);
  });
});

function dashboardFixture(): Dashboard {
  return {
    date: '2026-10-09',
    sales: { count: 3, grossSales: 220, discounts: 20, netSales: 200, averageTicket: 66.67, cancelledSales: 1 },
    fiscal: { pending: 2, awaitingAuthorization: 1, pendingConsultation: 0, offlineContingency: 0, pendingCancellation: 1 },
    payments: [{ paymentMethod: 'PIX', amount: 200 }],
    recentSales: [{ id: 10, saleAt: '2026-10-09T12:00:00Z', status: 'FISCALIZADA', total: 200, fiscalStatus: 'AUTORIZADA' }],
    dailyPerformance: [
      { date: '2026-10-08', sales: 1, total: 100 },
      { date: '2026-10-09', sales: 3, total: 200 },
    ],
  };
}
