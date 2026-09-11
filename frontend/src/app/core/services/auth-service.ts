import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { User } from '../../features/models/user';
import { LoginCredentials } from '../../features/models/login-credentials';
import { AuthResponse } from '../../features/models/auth-response';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly tokenKey = 'token';
  private readonly refreshTokenKey = 'refreshToken';

  // Sinal do estado de autenticação
  private readonly _isAuthenticated = signal<boolean>(
    !!localStorage.getItem(this.tokenKey)
  );
  readonly isAuthenticated = this._isAuthenticated.asReadonly();

  // Sinal reativo para guardar os dados do usuário vindo do backend
  private readonly _currentUser = signal<User | null>(null);
  readonly currentUser = this._currentUser.asReadonly();

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  login(credentials: LoginCredentials): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', credentials).pipe(
      tap((res) => {
        localStorage.setItem(this.tokenKey, res.accessToken);
        localStorage.setItem(this.refreshTokenKey, res.refreshToken);
        this._isAuthenticated.set(true);
      })
    );
  }

  // Busca os dados do usuário autenticado no backend
  fetchCurrentUser(): Observable<User> {
    return this.http.get<User>('/api/users/me').pipe(
      tap((user) => this._currentUser.set(user))
    );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.refreshTokenKey);
    this._isAuthenticated.set(false);
    this._currentUser.set(null);
    this.router.navigate(['/login']);
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem(this.refreshTokenKey);

    return this.http.post<AuthResponse>('/api/auth/refresh', { refreshToken }).pipe(
      tap((res) => {
        localStorage.setItem(this.tokenKey, res.accessToken);
        localStorage.setItem(this.refreshTokenKey, res.refreshToken);
      })
    );
  }

}
