import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable} from '@angular/core';
import { Observable } from 'rxjs';
import { SupplierDocumentDetailResponse, SupplierDocumentUploadRequest } from '../../features/models/supplier-document-dto';

@Injectable({ providedIn: 'root' })
export class SupplierDocumentService {
  private readonly http = inject(HttpClient);

  private baseUrl(supplierId: number): string {
    return `/api/suppliers/${supplierId}/documents`;
  }

  findAll(supplierId: number): Observable<SupplierDocumentDetailResponse[]> {
    return this.http.get<SupplierDocumentDetailResponse[]>(
      this.baseUrl(supplierId)
    );
  }

  findById(
    supplierId: number,
    documentId: number
  ): Observable<SupplierDocumentDetailResponse> {
    return this.http.get<SupplierDocumentDetailResponse>(
      `${this.baseUrl(supplierId)}/${documentId}`
    );
  }

  uploadNewDocument(
    supplierId: number,
    metadata: SupplierDocumentUploadRequest,
    file: File
  ): Observable<SupplierDocumentDetailResponse> {
    return this.http.post<SupplierDocumentDetailResponse>(
      this.baseUrl(supplierId),
      this.toMultipartBody(metadata, file)
    );
  }

  uploadNewVersion(
    supplierId: number,
    documentId: number,
    metadata: SupplierDocumentUploadRequest,
    file: File
  ): Observable<SupplierDocumentDetailResponse> {
    return this.http.post<SupplierDocumentDetailResponse>(
      `${this.baseUrl(supplierId)}/${documentId}/versions`,
      this.toMultipartBody(metadata, file)
    );
  }

  downloadLatest(
    supplierId: number,
    documentId: number
  ): Observable<HttpResponse<Blob>> {
    return this.http.get(
      `${this.baseUrl(supplierId)}/${documentId}/download`,
      {
        observe: 'response',
        responseType: 'blob',
      }
    );
  }

  downloadVersion(
    supplierId: number,
    documentId: number,
    versionId: number
  ): Observable<HttpResponse<Blob>> {
    return this.http.get(
      `${this.baseUrl(supplierId)}/${documentId}/versions/${versionId}/download`,
      {
        observe: 'response',
        responseType: 'blob',
      }
    );
  }

  delete(supplierId: number, documentId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl(supplierId)}/${documentId}`
    );
  }

  private toMultipartBody(
    metadata: SupplierDocumentUploadRequest,
    file: File
  ): FormData {
    const body = new FormData();

    body.append(
      'metadata',
      new Blob([JSON.stringify(metadata)], {
        type: 'application/json',
      })
    );

    body.append('file', file, file.name);

    return body;
  }
}
