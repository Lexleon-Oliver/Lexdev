import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { EnderecoViaCep } from '../../features/models/endereco-via-cep';

@Injectable({
  providedIn: 'root'
})
export class CepService {
  private readonly http = inject(HttpClient);

  buscarCep(cep: string): Observable<EnderecoViaCep> {
    const cepLimpo = cep.replace(/\D/g, '');
    return this.http.get<EnderecoViaCep>(`https://viacep.com.br/ws/${cepLimpo}/json/`);
  }
}
