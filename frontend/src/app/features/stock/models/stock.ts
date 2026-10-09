export type StockMovementType =
  | 'INITIAL_BALANCE'
  | 'PURCHASE_ENTRY'
  | 'SALE_OUT'
  | 'SALE_CANCELLATION_RETURN'
  | 'POSITIVE_ADJUSTMENT'
  | 'NEGATIVE_ADJUSTMENT';

export type StockMovementOrigin =
  | 'INITIAL_BALANCE'
  | 'PURCHASE'
  | 'SALE'
  | 'SALE_CANCELLATION'
  | 'MANUAL_ADJUSTMENT';

export interface StockBalance {
  productId: number;
  productCode: string;
  productName: string;
  unitOfMeasure: string;
  controlsStock: boolean;
  initialized: boolean;
  quantity: number | null;
}

export interface StockMovement {
  id: number;
  productId: number;
  movementType: StockMovementType;
  origin: StockMovementOrigin;
  sourceReference: string;
  quantity: number;
  previousBalance: number;
  resultingBalance: number;
  saleId: number | null;
  saleItemId: number | null;
  reason: string | null;
  createdBy: string;
  createdAt: string;
}

export interface StockOperationRequest {
  quantity: number;
  reason: string;
  operationReference: string;
}
