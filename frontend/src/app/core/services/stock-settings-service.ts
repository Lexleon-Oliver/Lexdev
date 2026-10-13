import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { StockSettings } from '../../features/stock/models/stock-settings';

@Injectable({ providedIn: 'root' })
export class StockSettingsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/stock/settings';
  find(): Observable<StockSettings> { return this.http.get<StockSettings>(this.apiUrl); }
  save(settings: StockSettings): Observable<StockSettings> { return this.http.put<StockSettings>(this.apiUrl, settings); }
}
