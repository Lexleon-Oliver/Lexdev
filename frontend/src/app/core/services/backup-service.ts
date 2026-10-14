import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BackupResponse } from '../../features/models/backup';

@Injectable({ providedIn: 'root' })
export class BackupService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/backups';

  findAll(): Observable<BackupResponse[]> {
    return this.http.get<BackupResponse[]>(this.apiUrl);
  }

  create(): Observable<BackupResponse> {
    return this.http.post<BackupResponse>(this.apiUrl, null);
  }

  download(fileName: string): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.apiUrl}/${encodeURIComponent(fileName)}`, {
      observe: 'response',
      responseType: 'blob',
    });
  }
}
