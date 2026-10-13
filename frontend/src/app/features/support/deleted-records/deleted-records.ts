import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

interface DeletedRecord {
  id: number;
  type: DeletedRecordType;
  title: string;
  subtitle: string | null;
  lastModifiedAt: string | null;
}

interface DeletedRecordPage {
  content: DeletedRecord[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

type DeletedRecordType = 'CLIENT' | 'SUPPLIER' | 'PRODUCT' | 'SUPPLIER_DOCUMENT' | 'USER';

@Component({
  selector: 'app-deleted-records',
  imports: [CommonModule, FormsModule],
  templateUrl: './deleted-records.html',
  styleUrl: './deleted-records.scss'
})
export class DeletedRecordsComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly types: { value: DeletedRecordType; label: string }[] = [
    { value: 'CLIENT', label: 'Clientes' },
    { value: 'SUPPLIER', label: 'Fornecedores' },
    { value: 'PRODUCT', label: 'Produtos' },
    { value: 'SUPPLIER_DOCUMENT', label: 'Documentos de fornecedores' },
    { value: 'USER', label: 'Usuários' }
  ];

  readonly pageSize = 20;

  selectedType: DeletedRecordType = 'CLIENT';
  search = '';
  records: DeletedRecord[] = [];
  currentPage = 0;
  totalElements = 0;
  totalPages = 0;
  loading = false;
  restoringId: number | null = null;
  error = '';
  success = '';

  showRestoreModal = false;
  restoringRecord: DeletedRecord | null = null;

  ngOnInit(): void {
    this.load();
  }
  load(): void {
    this.loading = true;
    this.error = '';

    const params = new HttpParams()
      .set('type', this.selectedType)
      .set('search', this.search.trim())
      .set('page', this.currentPage)
      .set('size', this.pageSize);

    this.http
      .get<DeletedRecordPage>('/api/support/deleted-records', { params })
      .pipe(
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: response => {
          this.records = response.content;
          this.currentPage = response.page;
          this.totalElements = response.totalElements;
          this.totalPages = response.totalPages;
        },
        error: error => {
          this.error =
            error?.error?.message ||
            'Não foi possível carregar os registros excluídos.';
        }
      });
  }

  searchRecords(): void {
    this.currentPage = 0;
    this.success = '';
    this.load();
  }

  changeType(type: DeletedRecordType): void {
    this.closeRestoreModal();
    this.success = '';
    this.selectedType = type;
    this.search = '';
    this.currentPage = 0;
    this.load();
  }

  previousPage(): void {
    if (this.loading || this.currentPage === 0) {
      return;
    }
    this.currentPage--;
    this.load();
  }

  nextPage(): void {
    if (this.loading || this.currentPage + 1 >= this.totalPages) {
      return;
    }
    this.currentPage++;
    this.load();
  }

  confirmRestore(record: DeletedRecord): void {
    if (this.restoringId !== null) {
      return;
    }

    this.error = '';
    this.success = '';
    this.restoringRecord = record;
    this.showRestoreModal = true;
  }

  closeRestoreModal(): void {
    if (this.restoringId !== null) {
      return;
    }

    this.showRestoreModal = false;
    this.restoringRecord = null;
  }

  restore(): void {
    const record = this.restoringRecord;

    if (!record || this.restoringId !== null) {
      return;
    }

    this.restoringId = record.id;
    this.error = '';
    this.success = '';

    this.http
      .patch<void>(`/api/support/deleted-records/${record.type}/${record.id}/restore`, {})
      .pipe(
        finalize(() => {
          this.restoringId = null;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          const pageWillBeEmpty = this.records.length === 1 && this.currentPage > 0;
          if (pageWillBeEmpty) {
            this.currentPage--;
          }
          this.showRestoreModal = false;
          this.restoringRecord = null;
          this.success = 'Registro recuperado com sucesso.';
          this.load();
        },
        error: error => {
          this.error = error?.error?.message || 'Não foi possível recuperar o registro.';
        }
      });
  }

  typeLabel(type: DeletedRecordType): string {
    return this.types.find(item => item.value === type)?.label ?? type;
  }
}
