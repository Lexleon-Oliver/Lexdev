import { HttpInterceptorFn, HttpErrorResponse, HttpEvent, HttpContextToken } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, catchError, filter, Observable, switchMap, take, throwError } from 'rxjs';
import { AuthService } from '../services/auth-service';

export const SKIP_REFRESH = new HttpContextToken<boolean>(
  () => false
);

/**
 * Controla se existe um refresh em andamento.
 */
let isRefreshing = false;

/**
 * Distribui o novo access token para as requisições
 * que chegaram enquanto o refresh estava acontecendo.
 */
const refreshTokenSubject =
  new BehaviorSubject<string | null>(null);


export const authInterceptor: HttpInterceptorFn = (req, next) => {

  const authService = inject(AuthService);
  const router = inject(Router);

  const token = authService.getToken();

  const isAuthRequest = req.url.includes('/api/auth/');

  const skipRefresh = req.context.get(SKIP_REFRESH);


  /*
   * Adiciona o access token nas requisições
   * autenticadas.
   *
   * As chamadas /auth/** não recebem o access token
   * automaticamente pelo interceptor.
   */
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

        /*
         * ============================================================
         * 401 - NÃO AUTORIZADO
         * ============================================================
         */
        if (
          error.status === 401 &&
          !isAuthRequest &&
          !skipRefresh
        ) {

          /*
           * ----------------------------------------------------------
           * Já existe um refresh acontecendo.
           *
           * Não fazemos outro POST /auth/refresh.
           * Aguardamos o primeiro terminar.
           * ----------------------------------------------------------
           */
          if (isRefreshing) {

            return refreshTokenSubject.pipe(

              /*
               * Espera aparecer um novo access token.
               */
              filter(
                (newToken): newToken is string =>
                  newToken !== null
              ),

              /*
               * Cada requisição pega apenas o próximo token.
               */
              take(1),

              /*
               * Repete a requisição original.
               */
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


          /*
           * ----------------------------------------------------------
           * Primeiro 401.
           *
           * Esta requisição será responsável pelo refresh.
           * ----------------------------------------------------------
           */
          isRefreshing = true;

          /*
           * Garante que as requisições que chegarem depois
           * aguardem um novo token.
           */
          refreshTokenSubject.next(null);


          return authService.refreshToken().pipe(

            /*
             * --------------------------------------------------------
             * Refresh realizado com sucesso.
             * --------------------------------------------------------
             */
            switchMap((res) => {

              const newToken = res.accessToken;


              /*
               * Libera as requisições que estavam esperando.
               */
              isRefreshing = false;

              refreshTokenSubject.next(newToken);


              /*
               * Repete a requisição que originalmente recebeu 401.
               *
               * IMPORTANTE:
               * SKIP_REFRESH = true
               *
               * Se esta tentativa também retornar 401,
               * não haverá outro refresh.
               */
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


            /*
             * --------------------------------------------------------
             * Falha no refresh.
             * --------------------------------------------------------
             */
            catchError((refreshError) => {

              isRefreshing = false;

              /*
               * Libera eventuais requisições esperando
               * pelo refresh.
               */
              refreshTokenSubject.next(null);

              /*
               * Access + refresh token não são mais confiáveis.
               */
              authService.logout();

              return throwError(() => refreshError);
            })
          );
        }


        /*
         * ============================================================
         * 403 - ACESSO NEGADO
         * ============================================================
         *
         * O usuário está autenticado, mas não possui
         * a permissão necessária.
         *
         * NÃO fazemos refresh.
         */
        if (error.status === 403) {

          router.navigate(['/403']);
        }


        /*
         * Qualquer outro erro continua normalmente.
         */
        return throwError(() => error);
      }
    )
  );
};
