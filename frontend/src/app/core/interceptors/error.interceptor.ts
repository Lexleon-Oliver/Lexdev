import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { inject } from '@angular/core';
import { ApiErrorResponse } from '../../features/models/api-error';
import { NotificationService } from '../services/notification-service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {

      const apiError = error.error as ApiErrorResponse;

      const fallbackMessage =
        'Ocorreu um erro inesperado no servidor.';

      const errorMessage =
        apiError?.message || fallbackMessage;

      // 401 é responsabilidade exclusiva do authInterceptor.
      // 403 também é tratado pelo authInterceptor.
      if (error.status !== 401 && error.status !== 403) {
        notificationService.error(errorMessage);
      }

      return throwError(() => error);
    })
  );
};
