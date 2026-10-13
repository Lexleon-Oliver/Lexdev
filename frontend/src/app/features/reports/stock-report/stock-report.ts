import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, finalize, Subject } from 'rxjs';
import { ReportService } from '../../../core/services/report-service';
import { StockLevelStatus, StockReport } from '../models/report';

@Component({ selector: 'app-stock-report', imports: [CommonModule, FormsModule], templateUrl: './stock-report.html', styleUrl: './stock-report.scss' })
export class StockReportComponent {
  private readonly service = inject(ReportService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchChanges = new Subject<string>();
  readonly loading = signal(false);
  readonly report = signal<StockReport | null>(null);
  readonly search = signal('');
  readonly currentPage = signal(0);
  readonly pageSize = 20;
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  startDate = monthStart();
  endDate = today();

  constructor() {
    this.searchChanges.pipe(debounceTime(350), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load(0));
    this.load();
  }

  load(page = this.currentPage()): void {
    if (!validPeriod(this.startDate, this.endDate)) return;
    this.loading.set(true);
    this.service.stock(this.startDate, this.endDate, page, this.pageSize, this.search())
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe(r => {
        this.report.set(r);
        this.currentPage.set(r.items.number ?? page);
        this.totalPages.set(r.items.totalPages ?? 0);
        this.totalElements.set(r.items.totalElements ?? 0);
      });
  }

  applyPeriod(): void { this.load(0); }
  setSearch(value: string): void { this.search.set(value); this.searchChanges.next(value.trim()); }
  loadPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.currentPage()) return;
    this.load(page);
  }

  stockStatusLabel(status: StockLevelStatus): string {
    const labels: Record<StockLevelStatus, string> = {
      NOT_CONTROLLED: 'Sem controle', NOT_INITIALIZED: 'Não inicializado', OUT_OF_STOCK: 'Sem estoque',
      BELOW_MINIMUM: 'Abaixo do mínimo', REORDER: 'Ponto de reposição', NORMAL: 'Normal', ABOVE_MAXIMUM: 'Acima do máximo',
    };
    return labels[status];
  }
  stockStatusClass(status: StockLevelStatus): string {
    const classes: Record<StockLevelStatus, string> = {
      NOT_CONTROLLED: 'muted', NOT_INITIALIZED: 'warning', OUT_OF_STOCK: 'danger', BELOW_MINIMUM: 'danger',
      REORDER: 'warning', NORMAL: 'success', ABOVE_MAXIMUM: 'info',
    };
    return classes[status];
  }
}
function today(): string { return localIso(new Date()); }
function monthStart(): string { const d = new Date(); d.setDate(1); return localIso(d); }
function localIso(d: Date): string { const y=d.getFullYear(), m=String(d.getMonth()+1).padStart(2,'0'), day=String(d.getDate()).padStart(2,'0'); return `${y}-${m}-${day}`; }
function validPeriod(a: string,b: string): boolean { return !!a && !!b && a <= b; }
