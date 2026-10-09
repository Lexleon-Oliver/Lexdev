import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ReportService } from '../../../core/services/report-service';
import { StockLevelStatus, StockReport } from '../models/report';

@Component({ selector: 'app-stock-report', imports: [CommonModule, FormsModule], templateUrl: './stock-report.html', styleUrl: './stock-report.scss' })
export class StockReportComponent {
  private readonly service = inject(ReportService);
  readonly loading = signal(false);
  readonly report = signal<StockReport | null>(null);
  readonly search = signal('');
  startDate = monthStart();
  endDate = today();

  readonly items = computed(() => {
    const q = this.search().trim().toLocaleLowerCase('pt-BR');
    const items = this.report()?.items ?? [];
    return q ? items.filter(i => i.code.toLocaleLowerCase('pt-BR').includes(q) || i.name.toLocaleLowerCase('pt-BR').includes(q)) : items;
  });

  constructor() { this.load(); }

  load(): void {
    if (!validPeriod(this.startDate, this.endDate)) return;
    this.loading.set(true);
    this.service.stock(this.startDate, this.endDate)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe(r => this.report.set(r));
  }

  setSearch(value: string): void { this.search.set(value); }

  stockStatusLabel(status: StockLevelStatus): string {
    const labels: Record<StockLevelStatus, string> = {
      NOT_CONTROLLED: 'Sem controle',
      NOT_INITIALIZED: 'Não inicializado',
      OUT_OF_STOCK: 'Sem estoque',
      BELOW_MINIMUM: 'Abaixo do mínimo',
      REORDER: 'Ponto de reposição',
      NORMAL: 'Normal',
      ABOVE_MAXIMUM: 'Acima do máximo',
    };
    return labels[status];
  }

  stockStatusClass(status: StockLevelStatus): string {
    const classes: Record<StockLevelStatus, string> = {
      NOT_CONTROLLED: 'muted',
      NOT_INITIALIZED: 'warning',
      OUT_OF_STOCK: 'danger',
      BELOW_MINIMUM: 'danger',
      REORDER: 'warning',
      NORMAL: 'success',
      ABOVE_MAXIMUM: 'info',
    };
    return classes[status];
  }
}

function today(): string { return localIso(new Date()); }
function monthStart(): string { const d = new Date(); d.setDate(1); return localIso(d); }
function localIso(d: Date): string { const y=d.getFullYear(), m=String(d.getMonth()+1).padStart(2,'0'), day=String(d.getDate()).padStart(2,'0'); return `${y}-${m}-${day}`; }
function validPeriod(a: string,b: string): boolean { return !!a && !!b && a <= b; }
