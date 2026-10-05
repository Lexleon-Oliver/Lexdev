import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, Input, OnChanges, OnInit, signal, SimpleChanges } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SupplierDocumentDetailResponse, SupplierDocumentUploadRequest, SupplierDocumentVersionResponse } from '../../models/supplier-document-dto';
import { HttpResponse } from '@angular/common/http';
import { NotificationService } from '../../../core/services/notification-service';
import { SupplierDocumentService } from '../../../core/services/supplier-document-service';
import { ActivatedRoute } from '@angular/router';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-supplier-documents',
  styleUrl: './supplier-documents.scss',
  templateUrl: './supplier-documents.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SupplierDocuments implements OnInit {

  private readonly documentService = inject(SupplierDocumentService);
  private readonly notification = inject(NotificationService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  supplierId: number | null = null;

  readonly documents = signal<SupplierDocumentDetailResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isUploading = signal(false);
  readonly replacingDocumentId = signal<number | null>(null);
  readonly deletingDocument = signal<SupplierDocumentDetailResponse | null>(null);

  readonly selectedFile = signal<File | null>(null);

  readonly allowedTypes = [
    'application/pdf',
    'image/jpeg',
    'image/png',
  ] as const;

  readonly form = this.fb.group({
    tipoDocumento: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(100),
    ]),
    numeroDocumento: this.fb.nonNullable.control('', Validators.maxLength(100)),
    dataEmissao: this.fb.nonNullable.control(''),
    dataValidade: this.fb.nonNullable.control(''),
    file: this.fb.control<File | null>(null, Validators.required),
  });

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');

    const supplierId = Number(idParam);

    if (!idParam || !Number.isInteger(supplierId) || supplierId <= 0) {
      this.supplierId = null;
      this.documents.set([]);

      this.notification.error('Fornecedor inválido.');
      return;
    }

    this.supplierId = supplierId;
    this.loadDocuments();
  }

  loadDocuments(): void {
    if (this.supplierId == null) {
      this.documents.set([]);
      return;
    }

    this.isLoading.set(true);

    this.documentService.findAll(this.supplierId).subscribe({
      next: (documents) => {
        this.documents.set(documents ?? []);
        this.isLoading.set(false);
      },
      error: () => {
        this.isLoading.set(false);
        this.notification.error('Erro ao carregar os documentos do fornecedor.');
      },
    });
  }

  beginNewDocument(): void {
    this.resetUploadState();
  }

  beginNewVersion(document: SupplierDocumentDetailResponse): void {
    this.replacingDocumentId.set(document.id);
    this.selectedFile.set(null);

    this.form.reset({
      tipoDocumento: document.tipoDocumento ?? '',
      numeroDocumento: document.numeroDocumento ?? '',
      dataEmissao: document.dataEmissao ?? '',
      dataValidade: document.dataValidade ?? '',
      file: null,
    });

    this.form.get('file')?.addValidators(Validators.required);
    this.form.get('file')?.updateValueAndValidity();
  }

  cancelUpload(): void {
    this.resetUploadState();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    if (!file) {
      this.selectedFile.set(null);
      this.form.get('file')?.setValue(null);
      return;
    }

    if (!this.isAllowedFile(file)) {
      this.selectedFile.set(null);
      this.form.get('file')?.setValue(null);
      input.value = '';
      this.notification.show(
        'Arquivo inválido. São aceitos apenas PDF, JPEG e PNG.',
        'warning'
      );
      return;
    }

    this.selectedFile.set(file);
    this.form.get('file')?.setValue(file);
    this.form.get('file')?.markAsTouched();
  }

  submitUpload(): void {
    if (this.supplierId == null) {
      this.notification.show(
        'Salve o fornecedor antes de adicionar documentos.',
        'warning'
      );
      return;
    }

    this.form.markAllAsTouched();

    const file = this.selectedFile();

    if (this.form.invalid || !file) {
      this.notification.show(
        'Informe os dados do documento e selecione um arquivo.',
        'warning'
      );
      return;
    }

    const metadata = this.toMetadata();
    const documentId = this.replacingDocumentId();

    this.isUploading.set(true);

    const request$ = documentId == null
      ? this.documentService.uploadNewDocument(
          this.supplierId,
          metadata,
          file
        )
      : this.documentService.uploadNewVersion(
          this.supplierId,
          documentId,
          metadata,
          file
        );

    request$.subscribe({
      next: () => {
        this.notification.success(
          documentId == null
            ? 'Documento enviado com sucesso.'
            : 'Nova versão enviada com sucesso.'
        );
        this.isUploading.set(false);
        this.resetUploadState();
        this.loadDocuments();
      },
      error: () => {
        this.isUploading.set(false);
        this.notification.error(
          documentId == null
            ? 'Erro ao enviar o documento.'
            : 'Erro ao enviar a nova versão.'
        );
      },
    });
  }

  askDelete(document: SupplierDocumentDetailResponse): void {
    this.deletingDocument.set(document);
  }

  cancelDelete(): void {
    this.deletingDocument.set(null);
  }

  confirmDelete(): void {
    const document = this.deletingDocument();

    if (this.supplierId == null || document == null) {
      return;
    }

    this.documentService.delete(this.supplierId, document.id).subscribe({
      next: () => {
        this.documents.update((items) =>
          items.filter((item) => item.id !== document.id)
        );
        this.deletingDocument.set(null);
        this.notification.success('Documento excluído com sucesso.');
      },
      error: () => {
        this.notification.error('Erro ao excluir o documento.');
      },
    });
  }

  downloadLatest(document: SupplierDocumentDetailResponse): void {
    const version = document.latestVersion;

    if (
      this.supplierId == null ||
      version == null ||
      version.scanStatus !== 'CLEAN'
    ) {
      return;
    }

    this.documentService
      .downloadLatest(this.supplierId, document.id)
      .subscribe({
        next: (response) => this.saveDownload(response, version.originalFileName),
        error: () =>
          this.notification.error('Não foi possível baixar o documento.'),
      });
  }

  downloadVersion(
    document: SupplierDocumentDetailResponse,
    version: SupplierDocumentVersionResponse
  ): void {
    if (
      this.supplierId == null ||
      version.scanStatus !== 'CLEAN'
    ) {
      return;
    }

    this.documentService
      .downloadVersion(this.supplierId, document.id, version.id)
      .subscribe({
        next: (response) => this.saveDownload(response, version.originalFileName),
        error: () =>
          this.notification.error('Não foi possível baixar esta versão.'),
      });
  }

  isReplacing(documentId: number): boolean {
    return this.replacingDocumentId() === documentId;
  }

  formatDate(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }

    const parts = value.split('-');

    if (parts.length !== 3) {
      return value;
    }

    const [year, month, day] = parts;
    return `${day}/${month}/${year}`;
  }

  formatFileSize(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes < 0) {
      return '—';
    }

    if (bytes < 1024) {
      return `${bytes} B`;
    }

    if (bytes < 1024 ** 2) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }

    if (bytes < 1024 ** 3) {
      return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
    }

    return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
  }

  scanStatusLabel(status: SupplierDocumentVersionResponse['scanStatus']): string {
    switch (status) {
      case 'CLEAN':
        return 'Arquivo liberado';
      case 'QUARANTINED':
        return 'Em quarentena';
      case 'PENDING_SCAN':
        return 'Aguardando análise';
      default:
        return status;
    }
  }

  private resetUploadState(): void {
    this.replacingDocumentId.set(null);
    this.selectedFile.set(null);

    this.form.reset({
      tipoDocumento: '',
      numeroDocumento: '',
      dataEmissao: '',
      dataValidade: '',
      file: null,
    });
  }

  private toMetadata(): SupplierDocumentUploadRequest {
    const raw = this.form.getRawValue();

    return {
      tipoDocumento: raw.tipoDocumento.trim(),
      numeroDocumento: raw.numeroDocumento.trim() || null,
      dataEmissao: raw.dataEmissao || null,
      dataValidade: raw.dataValidade || null,
    };
  }

  private isAllowedFile(file: File): boolean {
    if (this.allowedTypes.includes(file.type as typeof this.allowedTypes[number])) {
      return true;
    }

    const lowerName = file.name.toLowerCase();

    return ['.pdf', '.jpg', '.jpeg', '.png'].some((extension) =>
      lowerName.endsWith(extension)
    );
  }

  private saveDownload(response: HttpResponse<Blob>, fallbackName: string): void {
    if (!response.body) {
      this.notification.error('O servidor não retornou o arquivo.');
      return;
    }

    const fileName = this.extractFileName(
      response.headers.get('Content-Disposition')
    ) ?? fallbackName;

    const url = URL.createObjectURL(response.body);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private extractFileName(contentDisposition: string | null): string | null {
    if (!contentDisposition) {
      return null;
    }

    const encodedMatch = contentDisposition.match(/filename\\*=UTF-8''([^;]+)/i);

    if (encodedMatch?.[1]) {
      try {
        return decodeURIComponent(encodedMatch[1]);
      } catch {
        return encodedMatch[1];
      }
    }

    const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
    return plainMatch?.[1] ?? null;
  }
}
