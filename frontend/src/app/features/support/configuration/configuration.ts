import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { finalize } from 'rxjs';

export interface ConfigurationFeature {
  key: string;
  label: string;
  category: string;
  enabled: boolean;
  requiresRestart: boolean;
}

export interface ConfigurationArea {
  name: string;
  ownership: 'CLIENTE' | 'SUPORTE' | 'INSTALADOR';
  location: string;
  explanation: string;
}

export interface ConfigurationOverview {
  features: ConfigurationFeature[];
  areas: ConfigurationArea[];
}

@Component({
  selector: 'app-support-configuration',
  imports: [CommonModule],
  templateUrl: './configuration.html',
  styleUrl: './configuration.scss',
})
export class SupportConfigurationComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);

  overview: ConfigurationOverview | null = null;
  loading = false;
  error = '';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    if (this.loading) return;
    this.loading = true;
    this.error = '';

    this.http.get<ConfigurationOverview>('/api/support/configuration')
      .pipe(finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      }))
      .subscribe({
        next: result => { this.overview = result; },
        error: () => {
          this.overview = null;
          this.error = 'Não foi possível consultar as configurações técnicas.';
        },
      });
  }
}
