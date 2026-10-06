import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { Company, CompanyRequest } from '../../features/models/company-model';

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/company';

  find(): Observable<Company> {
    return this.http.get<Company>(this.apiUrl);
  }

  save(request: CompanyRequest): Observable<Company> {
    return this.http.put<Company>(this.apiUrl, request);
  }
}
