import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';
import { SupplierResponseDto } from '../../features/models/supplier-response-dto';
import { SupplierRequestDto } from '../../features/models/supplier-request-dto';
import { ViaCepResponse } from '../../features/models/via-cep';


@Injectable({ providedIn: 'root' })
export class SupplierService {
  private readonly apiUrl = '/api/suppliers';
  private readonly viaCepUrl = 'https://viacep.com.br/ws';

  constructor(private http: HttpClient) {}

  findAll(page = 0, size = 10, sort?: string): Observable<SpringPage<SupplierResponseDto>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    if (sort) params = params.set('sort', sort);
    return this.http.get<SpringPage<SupplierResponseDto>>(this.apiUrl, { params });
  }

  findById(id: number): Observable<SupplierResponseDto> {
    return this.http.get<SupplierResponseDto>(`${this.apiUrl}/${id}`);
  }

  create(dto: SupplierRequestDto): Observable<SupplierResponseDto> {
    return this.http.post<SupplierResponseDto>(this.apiUrl, dto);
  }

  update(id: number, dto: SupplierRequestDto): Observable<SupplierResponseDto> {
    return this.http.put<SupplierResponseDto>(`${this.apiUrl}/${id}`, dto);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getAddressByCep(cep: string): Observable<ViaCepResponse> {
    const clean = cep.replace(/\D/g, '');
    return this.http.get<ViaCepResponse>(`${this.viaCepUrl}/${clean}/json`);
  }
}
