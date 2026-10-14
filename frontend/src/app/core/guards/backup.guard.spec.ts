import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { backupGuard } from './backup.guard';
import { AuthService } from '../services/auth-service';

interface GuardAuthStub {
  currentUser: () => unknown;
  hasRole: (roles: string[]) => boolean;
  fetchCurrentUser: () => Observable<unknown>;
}

describe('backupGuard', () => {
  let allowed = false;
  let currentUser: unknown = { id: 1 };
  let auth: GuardAuthStub;

  beforeEach(() => {
    allowed = false;
    currentUser = { id: 1 };
    auth = {
      currentUser: () => currentUser,
      hasRole: () => allowed,
      fetchCurrentUser: () => of({ id: 1 }),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
  });

  const runGuard = () => TestBed.runInInjectionContext(() =>
    backupGuard({} as never, {} as never)
  );

  it('deve permitir ADMIN ou SUPPORT', () => {
    allowed = true;
    expect(runGuard()).toBe(true);
  });

  it('deve redirecionar perfis sem permissão para 403', () => {
    const result = runGuard();
    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/403');
  });

  it('deve carregar o usuário atual antes de decidir quando necessário', async () => {
    currentUser = null;
    allowed = true;
    const result = await firstValueFrom(runGuard() as Observable<boolean | UrlTree>);
    expect(result).toBe(true);
  });
});
