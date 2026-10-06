import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { ProductFiscalProfile, ProductFiscalProfileRequest } from '../../features/models/product-fiscal-profile';

@Injectable({ providedIn: 'root' })
export class ProductFiscalProfileService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api';

  findByProductId(productId: number): Observable<ProductFiscalProfile> {
    return this.http.get<ProductFiscalProfile>(`${this.apiUrl}/products/${productId}/fiscal-profile`);
  }

  save(productId: number, request: ProductFiscalProfileRequest): Observable<ProductFiscalProfile> {
    return this.http.put<ProductFiscalProfile>(`${this.apiUrl}/products/${productId}/fiscal-profile`, request);
  }
}
