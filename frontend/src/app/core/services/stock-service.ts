import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';
import { StockBalance, StockMovement, StockOperationRequest } from '../../features/stock/models/stock';

@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/stock/products';

  balance(productId: number): Observable<StockBalance> {
    return this.http.get<StockBalance>(`${this.apiUrl}/${productId}`);
  }

  movements(productId: number, page = 0, size = 20): Observable<SpringPage<StockMovement>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<SpringPage<StockMovement>>(`${this.apiUrl}/${productId}/movements`, { params });
  }

  initializeBalance(productId: number, request: StockOperationRequest): Observable<StockMovement> {
    return this.http.post<StockMovement>(`${this.apiUrl}/${productId}/initial-balance`, request);
  }

  adjustPositive(productId: number, request: StockOperationRequest): Observable<StockMovement> {
    return this.http.post<StockMovement>(`${this.apiUrl}/${productId}/adjustments/positive`, request);
  }

  adjustNegative(productId: number, request: StockOperationRequest): Observable<StockMovement> {
    return this.http.post<StockMovement>(`${this.apiUrl}/${productId}/adjustments/negative`, request);
  }
}
