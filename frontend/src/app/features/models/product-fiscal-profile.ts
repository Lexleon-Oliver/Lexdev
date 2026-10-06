export interface ProductFiscalProfile {
  id?: number;
  productId: number;
  cfop: string;
  icmsCstCsosn: string;
  pisCst: string;
  cofinsCst: string;
  icmsRate?: number | null;
  pisRate?: number | null;
  cofinsRate?: number | null;
  ibsCbsCst?: string | null;
  cClassTrib?: string | null;
  ibsRate?: number | null;
  cbsRate?: number | null;
  additionalInformation?: string | null;
  active: boolean;
}

export type ProductFiscalProfileRequest = Omit<ProductFiscalProfile, 'id' | 'productId' | 'active'>;
