export interface ProductSupplierRequest {
  supplierId: number;
  supplierCode?: string | null;
  purchasePrice?: number | null;
  leadTimeDays?: number | null;
  minimumOrderQuantity?: number | null;
  preferred?: boolean;
}
