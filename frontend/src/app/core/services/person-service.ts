import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';
import { PersonOption } from '../../features/models/person-option';

@Injectable({ providedIn: 'root' })
export class PersonService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/persons';

  findLegalEntities(q = '', page = 0, size = 20): Observable<SpringPage<PersonOption>> {
    const params = new HttpParams()
      .set('q', q)
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<SpringPage<PersonOption>>(`${this.apiUrl}/legal-entities`, { params });
  }
}
