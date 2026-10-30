import { HttpContextToken, HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { inject } from '@angular/core';
import { ApiErrorResponse } from '../../features/models/api-error';
import { NotificationService } from '../services/notification-service';
import { getLicenseHttpError } from '../licensing/license-http-error';

export const SKIP_ERROR_NOTIFICATION = new HttpContextToken<boolean>(() => false);

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {

      const apiError = error.error as ApiErrorResponse;
      const licenseError = getLicenseHttpError(error);

      const fallbackMessage =
        'Ocorreu um erro inesperado no servidor.';

      const errorMessage =
        apiError?.message || fallbackMessage;

      if (!req.context.get(SKIP_ERROR_NOTIFICATION)) {

        if (licenseError) {
          notificationService.error(
            `Operação bloqueada pela licença. ${licenseError.message}`
          );
        } else if (
          error.status !== 401 &&
          error.status !== 403
        ) {
          notificationService.error(errorMessage);
        }
      }

      return throwError(() => error);
    })
  );
};
