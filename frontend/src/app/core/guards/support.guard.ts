import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth-service';

export const supportGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const decide = () => auth.isSupport() ? true : router.createUrlTree(['/403']);
  if (auth.currentUser()) return decide();

  return auth.fetchCurrentUser().pipe(
    map(() => decide()),
    catchError(() => of(router.createUrlTree(['/login'])))
  );
};
