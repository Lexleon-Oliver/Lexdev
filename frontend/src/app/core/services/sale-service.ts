import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable} from '@angular/core';
import { Sale, SaleCreateRequest } from '../../features/models/sale';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';

@Injectable({ providedIn: 'root' })
export class SaleService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/sales';

  create(request: SaleCreateRequest): Observable<Sale> {
    return this.http.post<Sale>(this.apiUrl, request);
  }

  findById(id: number): Observable<Sale> {
    return this.http.get<Sale>(`${this.apiUrl}/${id}`);
  }

  findAll(page = 0, size = 20): Observable<SpringPage<Sale>> {
    const params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    return this.http.get<SpringPage<Sale>>(this.apiUrl, { params });
  }

  consult(id: number): Observable<Sale> {
    return this.http.post<Sale>(`${this.apiUrl}/${id}/consult`, {});
  }

  cancel(id: number, justification: string): Observable<Sale> {
    const params = new HttpParams().set('justification', justification);
    return this.http.post<Sale>(`${this.apiUrl}/${id}/cancel`, {}, { params });
  }

  downloadXml(id: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${id}/fiscal/xml`, { responseType: 'blob' });
  }
}
