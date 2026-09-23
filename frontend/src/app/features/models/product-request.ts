
import { ProductStatus } from "./product-status";
import { ProductSupplierRequest } from "./product-supplier-request";

export interface ProductRequest {

  code: string;

  name: string;

  description?: string | null;

  model?: string | null;

  manufacturerCode?: string | null;

  gtin?: string | null;

  status: ProductStatus;

  unitOfMeasure: string;

  controlsStock: boolean;

  salePrice?: number | null;

  minimumSalePrice?: number | null;

  minimumStock?: number | null;

  maximumStock?: number | null;

  reorderPoint?: number | null;

  ncm?: string | null;

  cest?: string | null;

  origin?: string | null;

  grossWeight?: number | null;

  netWeight?: number | null;

  height?: number | null;

  width?: number | null;

  length?: number | null;

  suppliers: ProductSupplierRequest[];
}
