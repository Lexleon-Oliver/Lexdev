export type DocumentScanStatus =
  | 'PENDING_SCAN'
  | 'CLEAN'
  | 'QUARANTINED';

export interface SupplierDocumentUploadRequest {
  tipoDocumento: string;
  numeroDocumento?: string | null;
  dataEmissao?: string | null;
  dataValidade?: string | null;
}

export interface SupplierDocumentVersionResponse {
  id: number;
  versionNumber: number;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  checksumSha256: string;
  scanStatus: DocumentScanStatus;
  createdAt: string;
}

export interface SupplierDocumentDetailResponse {
  id: number;
  tipoDocumento: string;
  numeroDocumento?: string | null;
  dataEmissao?: string | null;
  dataValidade?: string | null;
  latestVersion: SupplierDocumentVersionResponse | null;
  versions: SupplierDocumentVersionResponse[];
}
