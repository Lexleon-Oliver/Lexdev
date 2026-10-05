import { HttpInterceptorFn, HttpErrorResponse, HttpEvent, HttpContextToken } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, catchError, filter, Observable, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth-service';

let isRefreshing = false;

let refreshTokenSubject =
  new BehaviorSubject<string | null>(null);

export const SKIP_REFRESH =
  new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();
  const isAuthRequest = req.url.includes('/api/auth/');
  const skipRefresh = req.context.get(SKIP_REFRESH);

  let authReq = req;

  if (token && !isAuthRequest) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(authReq).pipe(
    catchError(
      (error: HttpErrorResponse): Observable<HttpEvent<unknown>> => {

        if (
          error.status === 401 &&
          !isAuthRequest &&
          !skipRefresh
        ) {

          if (isRefreshing) {
            return refreshTokenSubject.pipe(
              filter(
                (newToken): newToken is string =>
                  newToken !== null
              ),
              take(1),
              switchMap((newToken) => {

                const retryReq = req.clone({
                  context: req.context.set(
                    SKIP_REFRESH,
                    true
                  ),
                  setHeaders: {
                    Authorization: `Bearer ${newToken}`
                  }
                });

                return next(retryReq);
              })
            );
          }

          isRefreshing = true;
          refreshTokenSubject.next(null);

          return authService.refreshToken().pipe(

            switchMap((res) => {
              const newToken = res.accessToken;

              isRefreshing = false;

              refreshTokenSubject.next(newToken);

              const retryReq = req.clone({
                context: req.context.set(
                  SKIP_REFRESH,
                  true
                ),
                setHeaders: {
                  Authorization: `Bearer ${newToken}`
                }
              });

              return next(retryReq);
            }),

            catchError((refreshError) => {

              isRefreshing = false;

              refreshTokenSubject.error(refreshError);
              refreshTokenSubject =
                new BehaviorSubject<string | null>(null);

              authService.logout();

              return throwError(
                () => refreshError
              );
            })
          );
        }

        if (error.status === 403) {
          router.navigate(['/403']);
        }

        return throwError(() => error);
      }
    )
  );
};
