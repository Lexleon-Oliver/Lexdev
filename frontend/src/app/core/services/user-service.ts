import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { User, UserCreateRequest, UserUpdateRequest } from '../../features/models/user';
import { SpringPage } from '../../features/models/spring-page';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/users';

  findAll(page = 0, size = 10, query = ''): Observable<SpringPage<User>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    const normalizedQuery = query.trim();
    if (normalizedQuery) {
      params = params.set('q', normalizedQuery);
    }

    return this.http.get<SpringPage<User>>(this.apiUrl, { params });
  }

  findById(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  create(user: UserCreateRequest): Observable<User> {
    return this.http.post<User>(this.apiUrl, user);
  }

  update(id: number, user: UserUpdateRequest): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, user);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
