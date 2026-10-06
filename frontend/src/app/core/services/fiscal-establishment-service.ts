import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { FiscalEstablishment, FiscalEstablishmentRequest, FiscalServiceStatus } from '../../features/models/fiscal-establishment';

@Injectable({ providedIn: 'root' })
export class FiscalEstablishmentService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/fiscal/establishments';

  findAll(): Observable<FiscalEstablishment[]> {
    return this.http.get<FiscalEstablishment[]>(this.apiUrl);
  }

  create(request: FiscalEstablishmentRequest): Observable<FiscalEstablishment> {
    return this.http.post<FiscalEstablishment>(this.apiUrl, request);
  }

  update(id: number, request: FiscalEstablishmentRequest): Observable<FiscalEstablishment> {
    return this.http.put<FiscalEstablishment>(`${this.apiUrl}/${id}`, request);
  }

  uploadCertificate(id: number, file: File, password: string): Observable<void> {
    const body = new FormData();
    body.append('file', file);
    body.append('password', password);
    return this.http.post<void>(`${this.apiUrl}/${id}/certificate`, body);
  }

  checkStatus(id: number): Observable<FiscalServiceStatus> {
    return this.http.get<FiscalServiceStatus>(`${this.apiUrl}/${id}/status`);
  }
}
