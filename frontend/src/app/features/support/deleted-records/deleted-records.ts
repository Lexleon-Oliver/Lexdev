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

  selectedType: DeletedRecordType = 'CLIENT';
  search = '';
  records: DeletedRecord[] = [];
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
      .set('search', this.search.trim());

    this.http.get<DeletedRecord[]>('/api/support/deleted-records', { params }).subscribe({
      next: records => {
        this.records = records;
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.message || 'Não foi possível carregar os registros excluídos.';
        this.loading = false;
      }
    });
  }

  changeType(type: DeletedRecordType): void {
    this.closeRestoreModal();
    this.success = '';
    this.selectedType = type;
    this.search = '';
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
          /*
           * O estado transitório pertence ao ciclo de vida da requisição,
           * portanto deve ser encerrado independentemente de sucesso ou erro.
           * O detectChanges explícito garante que o modal não permaneça
           * visualmente em "Recuperando..." depois que o HTTP já terminou.
           */
          this.restoringId = null;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.records = this.records.filter(
            item => !(item.id === record.id && item.type === record.type)
          );
          this.showRestoreModal = false;
          this.restoringRecord = null;
          this.success = 'Registro recuperado com sucesso.';
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
