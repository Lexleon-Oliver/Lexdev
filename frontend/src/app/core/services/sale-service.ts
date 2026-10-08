import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable} from '@angular/core';
import { Sale, SaleCreateRequest } from '../../features/models/sale';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';
import { SKIP_ERROR_NOTIFICATION } from '../interceptors/error.interceptor';

@Injectable({ providedIn: 'root' })
export class SaleService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/sales';

  create(request: SaleCreateRequest): Observable<Sale> {
    return this.http.post<Sale>(this.apiUrl, request);
  }

  issue(id: number): Observable<Sale> {
    const context = new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
    return this.http.post<Sale>(`${this.apiUrl}/${id}/issue`, {}, { context });
  }

  findById(id: number): Observable<Sale> {
    return this.http.get<Sale>(`${this.apiUrl}/${id}`);
  }

  findAll(page = 0, size = 20): Observable<SpringPage<Sale>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<SpringPage<Sale>>(this.apiUrl, { params });
  }

  findFiscalPending(page = 0, size = 20): Observable<SpringPage<Sale>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<SpringPage<Sale>>(`${this.apiUrl}/fiscal-pending`, { params });
  }

  prepareOfflineContingency(id: number, justification: string): Observable<Sale> {
    const context = new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
    const params = new HttpParams().set('justification', justification);
    return this.http.post<Sale>(`${this.apiUrl}/${id}/contingency/offline`, {}, { params, context });
  }

  transmitOfflineContingency(id: number): Observable<Sale> {
    const context = new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
    return this.http.post<Sale>(`${this.apiUrl}/${id}/contingency/transmit`, {}, { context });
  }

  consult(id: number): Observable<Sale> {
    return this.http.post<Sale>(`${this.apiUrl}/${id}/consult`, {});
  }

  cancel(id: number, justification: string): Observable<Sale> {
    const params = new HttpParams().set('justification', justification);
    return this.http.post<Sale>(`${this.apiUrl}/${id}/cancel`, {}, { params });
  }

  downloadDanfe(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/fiscal/danfe`, { responseType: 'blob' });
  }

  downloadXml(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/fiscal/xml`, { responseType: 'blob' });
  }
}
