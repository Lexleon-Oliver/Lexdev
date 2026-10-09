import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Dashboard } from '../../features/models/dashboard';


@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/dashboard';

  load(): Observable<Dashboard> {
    return this.http.get<Dashboard>(this.apiUrl);
  }
}
