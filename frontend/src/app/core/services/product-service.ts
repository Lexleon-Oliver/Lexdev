import { inject, Service } from '@angular/core';
import { Product } from '../../features/models/product';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ProductRequest } from '../../features/models/product-request';

@Service()
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/products';

  findAll(page = 0, size = 10): Observable<SpringPage<Product>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<SpringPage<Product>>(this.apiUrl, { params });
  }

  findById(id: number): Observable<Product> {
    return this.http.get<Product>(`${this.apiUrl}/${id}`);
  }

  create(product: Partial<ProductRequest>): Observable<Product> {
    return this.http.post<Product>(this.apiUrl, product);
  }

  update(id: number, product: Partial<ProductRequest>): Observable<Product> {
    return this.http.put<Product>(`${this.apiUrl}/${id}`, product);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
