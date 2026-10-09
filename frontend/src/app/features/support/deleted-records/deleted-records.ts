import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';

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

    this.http.patch<void>(`/api/support/deleted-records/${record.type}/${record.id}/restore`, {}).subscribe({
      next: () => {
        /*
         * A restauração já foi confirmada pelo backend. Removemos o item da
         * coleção local em vez de disparar um segundo GET imediatamente.
         * Isso evita colocar a tela inteira novamente em estado de loading e
         * mantém a UI coerente: um registro restaurado não pertence mais à
         * listagem de excluídos.
         */
        this.records = this.records.filter(item => !(item.id === record.id && item.type === record.type));
        this.restoringId = null;
        this.showRestoreModal = false;
        this.restoringRecord = null;
        this.success = 'Registro recuperado com sucesso.';
      },
      error: error => {
        this.error = error?.error?.message || 'Não foi possível recuperar o registro.';
        this.restoringId = null;
      }
    });
  }

  typeLabel(type: DeletedRecordType): string {
    return this.types.find(item => item.value === type)?.label ?? type;
  }
}
