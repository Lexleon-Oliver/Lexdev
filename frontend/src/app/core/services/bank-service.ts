import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, shareReplay } from 'rxjs';
import { Bank, BrasilApiBank } from '../../features/models/bank';
import { SKIP_ERROR_NOTIFICATION } from '../interceptors/error.interceptor';

@Injectable({ providedIn: 'root' })
export class BankService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'https://brasilapi.com.br/api/banks/v1';

  /** Lista mínima usada se a API pública estiver fora do ar. */
  private readonly fallbackBanks: Bank[] = [
    [1, 'BCO DO BRASIL S.A.'],
    [33, 'BCO SANTANDER (BRASIL) S.A.'],
    [104, 'CAIXA ECONOMICA FEDERAL'],
    [237, 'BCO BRADESCO S.A.'],
    [260, 'NU PAGAMENTOS S.A. - INSTITUIÇÃO DE PAGAMENTO'],
    [341, 'ITAÚ UNIBANCO S.A.'],
    [336, 'BCO C6 S.A.'],
    [77, 'BANCO INTER S.A.'],
    [756, 'BANCO COOPERATIVO DO BRASIL S.A. - BANCOOB'],
    [748, 'BCO COOPERATIVO SICREDI S.A.'],
    [422, 'BCO SAFRA S.A.'],
    [212, 'BANCO ORIGINAL'],
    [70, 'BRB - BCO DE BRASILIA S.A.'],
    [4, 'BCO DO NORDESTE DO BRASIL S.A.'],
    [41, 'BCO DO ESTADO DO RS S.A.'],
  ].map(([code, name]) => this.toBank(code as number, name as string, name as string));

  /** Lista carregada uma única vez e reaproveitada nas próximas aberturas. */
  private banks$?: Observable<Bank[]>;

  getBanks(): Observable<Bank[]> {
    if (!this.banks$) {
      this.banks$ = this.http
        .get<BrasilApiBank[]>(this.apiUrl, {
          // falha silenciosa: o usuário ainda pode digitar o banco manualmente
          context: new HttpContext().set(SKIP_ERROR_NOTIFICATION, true),
        })
        .pipe(
          map((list) =>
            list
              .filter((b) => b.code != null && !!b.name)
              .map((b) => this.toBank(b.code as number, b.name as string, b.fullName ?? (b.name as string)))
              .sort((a, b) => a.code.localeCompare(b.code))
          ),
          catchError(() => {
            this.banks$ = undefined; // permite nova tentativa depois
            return of(this.fallbackBanks);
          }),
          shareReplay({ bufferSize: 1, refCount: false })
        );
    }
    return this.banks$;
  }

  private toBank(code: number, name: string, fullName: string): Bank {
    const padded = String(code).padStart(3, '0');
    return { code: padded, name, fullName, label: `${padded} - ${name}` };
  }
}
