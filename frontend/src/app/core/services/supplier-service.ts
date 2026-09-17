import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';
import { Supplier } from '../../features/models/supplier';

@Injectable({
  providedIn: 'root'
})
export class SupplierService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/suppliers';

  findAll(page = 0, size = 10): Observable<SpringPage<Supplier>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<SpringPage<Supplier>>(this.apiUrl, { params });
  }

  findById(id: number): Observable<Supplier> {
    return this.http.get<Supplier>(`${this.apiUrl}/${id}`);
  }

  create(supplier: Partial<Supplier>): Observable<Supplier> {
    return this.http.post<Supplier>(this.apiUrl, supplier);
  }

  update(id: number, supplier: Partial<Supplier>): Observable<Supplier> {
    return this.http.put<Supplier>(`${this.apiUrl}/${id}`, supplier);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
