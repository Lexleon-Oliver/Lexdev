import { ProductImage } from "./product-image";
import { ProductStatus } from "./product-status";
import { ProductSupplier } from "./product-supplier";


export interface Product {
  id?: number;
  code: string;
  name: string;
  description?: string | null;
  model?: string | null;
  manufacturerCode?: string | null;
  gtin?: string | null;
  status: ProductStatus;
  unitOfMeasure: string;
  controlsStock: boolean;
  controlsExpiration?: boolean;
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
  suppliers: ProductSupplier[];
  images: ProductImage[];
  active: boolean;
}
