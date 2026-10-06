export type FiscalEnvironment = 'HOMOLOGACAO' | 'PRODUCAO';
export type TaxRegime = 'SIMPLES_NACIONAL' | 'REGIME_NORMAL';

export interface FiscalEstablishment {
  id?: number;
  companyId: number;
  cnpj: string;
  legalName: string;
  tradeName?: string | null;
  stateRegistration?: string | null;
  municipalityIbgeCode: string;
  taxRegime: TaxRegime;
  environment: FiscalEnvironment;
  series: number;
  nextNumber: number;
  cscId?: number | null;
  hasCsc: boolean;
  hasCertificate: boolean;
  active: boolean;
}

export interface FiscalEstablishmentRequest {
  municipalityIbgeCode: string;
  taxRegime: TaxRegime;
  environment: FiscalEnvironment;
  series: number;
  nextNumber: number;
  cscId?: number | null;
  csc?: string | null;
  certificatePassword?: string | null;
}

export interface FiscalServiceStatus {
  statusCode?: string | null;
  reason?: string | null;
  mappedStatus: string;
  checkedAt: string;
}
