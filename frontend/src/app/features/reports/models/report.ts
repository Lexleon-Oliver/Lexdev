export type StockLevelStatus =
  | 'NOT_CONTROLLED'
  | 'NOT_INITIALIZED'
  | 'OUT_OF_STOCK'
  | 'BELOW_MINIMUM'
  | 'REORDER'
  | 'NORMAL'
  | 'ABOVE_MAXIMUM';

export interface StockReportItem {
  productId: number;
  code: string;
  name: string;
  unitOfMeasure: string;
  controlsStock: boolean;
  initialized: boolean;
  currentBalance: number | null;
  stockStatus: StockLevelStatus;
  replenishmentNeeded: boolean;
  minimumStock: number | null;
  maximumStock: number | null;
  reorderPoint: number | null;
  salePrice: number | null;
  stockEntries: number;
  stockOutputs: number;
  quantitySold: number;
  salesValue: number;
}

export interface StockReport {
  startDate: string;
  endDate: string;
  products: number;
  stockControlledProducts: number;
  initializedStockProducts: number;
  replenishmentNeededProducts: number;
  currentBalance: number;
  stockEntries: number;
  stockOutputs: number;
  quantitySold: number;
  salesValue: number;
  items: StockReportItem[];
}

export type PaymentMethod = 'DINHEIRO' | 'CREDITO' | 'DEBITO' | 'PIX' | 'OUTRO';
export interface FinancialPayment { paymentMethod: PaymentMethod; amount: number; }
export interface FinancialDaily { date: string; sales: number; total: number; }
export interface FinancialReport {
  startDate: string; endDate: string; sales: number; cancelledSales: number; grossSales: number;
  discounts: number; netSales: number; averageTicket: number; payments: FinancialPayment[]; daily: FinancialDaily[];
}
