import { ProductStatus } from './product-status';

export interface ProductSummary {
  id: number;
  code: string;
  name: string;
  model?: string | null;
  gtin?: string | null;
  status: ProductStatus;
  unitOfMeasure: string;
  controlsStock: boolean;
  controlsExpiration: boolean;
  salePrice?: number | null;
  minimumStock?: number | null;
  active: boolean;
}

export interface ProductSaleSummary {
  id: number;
  code: string;
  name: string;
  gtin?: string | null;
  unitOfMeasure: string;
  salePrice?: number | null;
  controlsStock: boolean;
  controlsExpiration: boolean;
}
