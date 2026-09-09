import { HttpClient } from '@angular/common/http';
import { Injectable, Service } from '@angular/core';
import { User } from '../../features/models/user';
import { Observable } from 'rxjs';
import { SpringPage } from '../../features/models/spring-page';

@Injectable({ providedIn: 'root' })
export class UserService {
    private baseUrl = '/api/users';

  constructor(private http: HttpClient) {}

  getAll(page: number = 0, size: number = 10): Observable<SpringPage<User>> {
    return this.http.get<SpringPage<User>>(`${this.baseUrl}?page=${page}&size=${size}`);
  }

  getById(id: number): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/${id}`);
  }

  create(user: Partial<User>): Observable<User> {
    return this.http.post<User>(this.baseUrl, user);
  }

  update(id: number, user: Partial<User>): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/${id}`, user);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
