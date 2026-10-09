import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { finalize } from 'rxjs';
import { AuthService } from '../../../../core/services/auth-service';
import { DashboardService } from '../../../../core/services/dashboard-service';
import { NotificationService } from '../../../../core/services/notification-service';
import { Dashboard, DashboardDaily } from '../../../models/dashboard';
import { FiscalDocumentStatus, PaymentMethod, SaleStatus } from '../../../models/sale';

@Component({
  imports: [CurrencyPipe, DatePipe],
  standalone: true,
  selector: 'app-home-component',
  styleUrl: './home-component.scss',
  templateUrl: './home-component.html',
})
export class HomeComponent implements OnInit {
  readonly authService = inject(AuthService);

  private readonly dashboardService = inject(DashboardService);
  private readonly notificationService = inject(NotificationService);

  readonly loading = signal(false);
  readonly dashboard = signal<Dashboard | null>(null);
  readonly error = signal<string | null>(null);

  readonly hasFiscalPending = computed(() => (this.dashboard()?.fiscal.pending ?? 0) > 0);
  readonly hasRecentSales = computed(() => (this.dashboard()?.recentSales.length ?? 0) > 0);
  readonly hasPayments = computed(() => (this.dashboard()?.payments.length ?? 0) > 0);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.dashboardService.load()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: dashboard => this.dashboard.set(dashboard),
        error: () => {
          const message = 'Não foi possível carregar o dashboard.';
          this.error.set(message);
          this.notificationService.error(message);
        },
      });
  }

  refresh(): void {
    this.load();
  }

  paymentLabel(method: PaymentMethod): string {
    const labels: Record<PaymentMethod, string> = {
      DINHEIRO: 'Dinheiro',
      CREDITO: 'Crédito',
      DEBITO: 'Débito',
      PIX: 'PIX',
      OUTRO: 'Outro',
    };
    return labels[method];
  }

  saleStatusLabel(status: SaleStatus): string {
    const labels: Record<SaleStatus, string> = {
      AGUARDANDO_FISCAL: 'Aguardando fiscal',
      FISCALIZADA: 'Fiscalizada',
      FISCAL_PENDENTE: 'Fiscal pendente',
      FISCAL_REJEITADA: 'Fiscal rejeitada',
      CANCELADA: 'Cancelada',
    };
    return labels[status];
  }

  fiscalStatusLabel(status: FiscalDocumentStatus | null): string {
    if (!status) {
      return 'Sem documento fiscal';
    }

    const labels: Record<FiscalDocumentStatus, string> = {
      AGUARDANDO_AUTORIZACAO: 'Aguardando autorização',
      PENDENTE_CONSULTA: 'Consulta pendente',
      AUTORIZADA: 'Autorizada',
      REJEITADA: 'Rejeitada',
      CANCELAMENTO_PENDENTE: 'Cancelamento pendente',
      CANCELADA: 'Cancelada',
      CONTINGENCIA: 'Contingência',
    };
    return labels[status];
  }

  maxDailyTotal(daily: DashboardDaily[] = this.dashboard()?.dailyPerformance ?? []): number {
    return daily.reduce((max, item) => Math.max(max, item.total), 0);
  }

  barWidth(value: number, max: number = this.maxDailyTotal()): number {
    if (max <= 0 || value <= 0) {
      return 0;
    }
    return Math.min(100, (value / max) * 100);
  }
}
