import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { FinancialReport, StockReport } from '../../features/reports/models/report';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/reports';

  stock(startDate: string, endDate: string): Observable<StockReport> {
    return this.http.get<StockReport>(`${this.apiUrl}/stock`, { params: this.period(startDate, endDate) });
  }
  financial(startDate: string, endDate: string): Observable<FinancialReport> {
    return this.http.get<FinancialReport>(`${this.apiUrl}/financial`, { params: this.period(startDate, endDate) });
  }
  private period(startDate: string, endDate: string): HttpParams {
    return new HttpParams().set('startDate', startDate).set('endDate', endDate);
  }
}
