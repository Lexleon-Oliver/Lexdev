import { FiscalDocumentStatus, PaymentMethod, SaleStatus } from '../models/sale';

export interface DashboardSales {
  count: number;
  grossSales: number;
  discounts: number;
  netSales: number;
  averageTicket: number;
  cancelledSales: number;
}

export interface DashboardFiscal {
  pending: number;
  awaitingAuthorization: number;
  pendingConsultation: number;
  offlineContingency: number;
  pendingCancellation: number;
}

export interface DashboardPayment {
  paymentMethod: PaymentMethod;
  amount: number;
}

export interface DashboardRecentSale {
  id: number;
  saleAt: string;
  status: SaleStatus;
  total: number;
  fiscalStatus: FiscalDocumentStatus | null;
}

export interface DashboardDaily {
  date: string;
  sales: number;
  total: number;
}

export interface Dashboard {
  date: string;
  sales: DashboardSales;
  fiscal: DashboardFiscal;
  payments: DashboardPayment[];
  recentSales: DashboardRecentSale[];
  dailyPerformance: DashboardDaily[];
}
