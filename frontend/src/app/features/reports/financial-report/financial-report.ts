import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { ReportService } from '../../../core/services/report-service';
import { FinancialReport, PaymentMethod } from '../models/report';

@Component({ selector: 'app-financial-report', imports: [CommonModule, FormsModule], templateUrl: './financial-report.html', styleUrl: './financial-report.scss' })
export class FinancialReportComponent {
  private readonly service = inject(ReportService);
  readonly loading = signal(false); readonly report = signal<FinancialReport | null>(null);
  startDate = monthStart(); endDate = today();
  constructor() { this.load(); }
  load(): void { if (!this.startDate || !this.endDate || this.startDate > this.endDate) return; this.loading.set(true); this.service.financial(this.startDate, this.endDate).pipe(finalize(() => this.loading.set(false))).subscribe(r => this.report.set(r)); }
  paymentLabel(method: PaymentMethod): string { return ({DINHEIRO:'Dinheiro',CREDITO:'Crédito',DEBITO:'Débito',PIX:'PIX',OUTRO:'Outro'})[method]; }
  barWidth(value: number, max: number): number { return max <= 0 ? 0 : Math.max(2, value / max * 100); }
  maxDaily(r: FinancialReport): number { return Math.max(0, ...r.daily.map(d => d.total)); }
}
function today(): string { return localIso(new Date()); }
function monthStart(): string { const d=new Date(); d.setDate(1); return localIso(d); }
function localIso(d: Date): string { return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
