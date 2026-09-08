import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { ApiErrorResponse } from '../../features/models/api-error';
import { NotificationService } from '../services/notification-service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const apiError = error.error as ApiErrorResponse;
      const fallbackMessage = 'Ocorreu um erro inesperado no servidor.';
      const errorMessage = apiError?.message || fallbackMessage;

      if (error.status === 401) {
        notificationService.error('Sessão expirada ou não autorizada. Faça login novamente.');
        router.navigate(['/login']);
      } else if (error.status === 403) {
        notificationService.error('Acesso negado: Você não possui permissão.');
        router.navigate(['/403']);
      } else {
        // Exibe a mensagem exata retornada pela ApiErrorResponse
        notificationService.error(errorMessage);
      }

      return throwError(() => apiError || error);
    })
  );
};
