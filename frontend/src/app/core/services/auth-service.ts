import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { User } from '../../features/models/user';
export interface LoginCredentials {
  username?: string | null;
  password?: string | null;
}
export interface AuthResponse {
  token: string;
  tokenType: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly tokenKey = 'token';

  private readonly _isAuthenticated = signal(
    !!localStorage.getItem(this.tokenKey)
  );
  readonly isAuthenticated = this._isAuthenticated.asReadonly();

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  login(credentials: LoginCredentials): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', credentials).pipe(
      tap((res) => {
        localStorage.setItem(this.tokenKey, res.token);
        this._isAuthenticated.set(true);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this._isAuthenticated.set(false);
    this.router.navigate(['/login']);
  }

  // src/app/services/auth.service.ts

getUser(): User | null {
  const token = this.getToken();
  if (!token) return null;

  try {
    // Exemplo extraindo o payload do JWT (base64)
    const payload = JSON.parse(atob(token.split('.')[1]));
    return { username: payload.sub || payload.username };
  } catch {
    return null;
  }
}
}
