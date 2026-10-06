export type SaleStatus = 'AGUARDANDO_FISCAL' | 'FISCALIZADA' | 'FISCAL_PENDENTE' | 'FISCAL_REJEITADA' | 'CANCELADA';
export type FiscalDocumentStatus = 'AGUARDANDO_AUTORIZACAO' | 'PENDENTE_CONSULTA' | 'AUTORIZADA' | 'REJEITADA' | 'CANCELAMENTO_PENDENTE' | 'CANCELADA' | 'CONTINGENCIA';
export type FiscalEmissionType = 'NORMAL' | 'CONTINGENCIA_OFFLINE';
export type PaymentMethod = 'DINHEIRO' | 'CREDITO' | 'DEBITO' | 'PIX' | 'OUTRO';

export interface SaleItemRequest {
  productId: number;
  quantity: number;
  unitPrice?: number | null;
  discount?: number | null;
}

export interface SalePaymentRequest {
  paymentMethod: PaymentMethod;
  amount: number;
  cardBrand?: string | null;
  authorizationCode?: string | null;
}

export interface SaleCreateRequest {
  fiscalEstablishmentId: number;
  clientId?: number | null;
  consumerCpfCnpj?: string | null;
  items: SaleItemRequest[];
  payments: SalePaymentRequest[];
  discount?: number | null;
  note?: string | null;
}

export interface FiscalDocument {
  id: number;
  model: string;
  series: number;
  number: number;
  accessKey?: string | null;
  emissionType: FiscalEmissionType;
  status: FiscalDocumentStatus;
  protocol?: string | null;
  receiptNumber?: string | null;
  reason?: string | null;
  issuedAt?: string | null;
  canceledAt?: string | null;
  cancellationProtocol?: string | null;
}

export interface SaleItem {
  id: number;
  itemNumber: number;
  productId: number;
  code: string;
  name: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  discount: number;
  total: number;
}

export interface SalePayment {
  id: number;
  paymentMethod: PaymentMethod;
  amount: number;
  cardBrand?: string | null;
  authorizationCode?: string | null;
}

export interface Sale {
  id: number;
  fiscalEstablishmentId: number;
  clientId?: number | null;
  userId: number;
  status: SaleStatus;
  saleAt: string;
  subtotal: number;
  discount: number;
  total: number;
  totalPaid: number;
  change: number;
  items: SaleItem[];
  payments: SalePayment[];
  fiscalDocument?: FiscalDocument | null;
  note?: string | null;
}
