import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Dashboard } from '../../features/models/dashboard';
import { DashboardInsight } from '../../features/models/dashboard-insight';


@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/dashboard';

  load(): Observable<Dashboard> {
    return this.http.get<Dashboard>(this.apiUrl);
  }

  loadInsight(): Observable<DashboardInsight> {
    return this.http.get<DashboardInsight>(`${this.apiUrl}/insights`);
  }
}
