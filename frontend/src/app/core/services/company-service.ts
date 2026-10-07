import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { Company, CompanyRequest } from '../../features/models/company-model';

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/company';

  /**
   * Retorna null quando a empresa proprietária ainda não foi cadastrada.
   * O backend usa HTTP 404 para representar essa condição de negócio.
   */
  find(): Observable<Company | null> {
    return this.http.get<Company>(this.apiUrl).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 404) {
          return of(null);
        }

        return throwError(() => error);
      }),
    );
  }

  save(request: CompanyRequest): Observable<Company> {
    return this.http.put<Company>(this.apiUrl, request);
  }
}
