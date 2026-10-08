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
  contingencyAt?: string | null;
  contingencyJustification?: string | null;
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

export interface FiscalActions {
  issueNormal: boolean;
  prepareOfflineContingency: boolean;
  transmitOfflineContingency: boolean;
  consult: boolean;
  consultPendingCancellation: boolean;
  downloadDanfe: boolean;
  downloadXml: boolean;
  cancel: boolean;
}

export function fiscalActionsFor(sale: Sale): FiscalActions {
  const document = sale.fiscalDocument;
  if (!document) {
    return noFiscalActions();
  }

  const normalAwaitingAuthorization =
    document.status === 'AGUARDANDO_AUTORIZACAO'
    && document.emissionType === 'NORMAL';

  const offlineContingency =
    document.status === 'CONTINGENCIA'
    && document.emissionType === 'CONTINGENCIA_OFFLINE';

  const authorized = document.status === 'AUTORIZADA';
  const pendingConsultation = document.status === 'PENDENTE_CONSULTA';
  const pendingCancellation = document.status === 'CANCELAMENTO_PENDENTE';

  return {
    issueNormal: normalAwaitingAuthorization,
    prepareOfflineContingency: normalAwaitingAuthorization,
    transmitOfflineContingency: offlineContingency,
    consult: pendingConsultation,
    consultPendingCancellation: pendingCancellation,
    downloadDanfe: authorized || offlineContingency,
    downloadXml: Boolean(document.accessKey) && (authorized || offlineContingency || pendingConsultation),
    cancel: authorized && sale.status === 'FISCALIZADA',
  };
}

function noFiscalActions(): FiscalActions {
  return {
    issueNormal: false,
    prepareOfflineContingency: false,
    transmitOfflineContingency: false,
    consult: false,
    consultPendingCancellation: false,
    downloadDanfe: false,
    downloadXml: false,
    cancel: false,
  };
}
