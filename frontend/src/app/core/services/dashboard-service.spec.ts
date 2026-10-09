import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardService } from './dashboard-service';
import { Dashboard } from '../../features/dashboard/models/dashboard';

describe('DashboardService', () => {
  let service: DashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should load the dashboard from the consolidated endpoint', () => {
    const response = dashboardFixture();

    service.load().subscribe(result => expect(result).toEqual(response));

    const request = http.expectOne('/api/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush(response);
  });
});

function dashboardFixture(): Dashboard {
  return {
    date: '2026-10-09',
    sales: { count: 2, grossSales: 110, discounts: 10, netSales: 100, averageTicket: 50, cancelledSales: 1 },
    fiscal: { pending: 1, awaitingAuthorization: 1, pendingConsultation: 0, offlineContingency: 0, pendingCancellation: 0 },
    payments: [{ paymentMethod: 'PIX', amount: 100 }],
    recentSales: [],
    dailyPerformance: [],
  };
}
