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
    { value: 'CLIENT', label: 'Clientes' }, { value: 'SUPPLIER', label: 'Fornecedores' },
    { value: 'PRODUCT', label: 'Produtos' }, { value: 'SUPPLIER_DOCUMENT', label: 'Documentos de fornecedores' },
    { value: 'USER', label: 'Usuários' }
  ];
  selectedType: DeletedRecordType = 'CLIENT'; search = ''; records: DeletedRecord[] = [];
  loading = false; restoringId: number | null = null; error = ''; success = '';

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading = true; this.error = '';
    const params = new HttpParams().set('type', this.selectedType).set('search', this.search.trim());
    this.http.get<DeletedRecord[]>('/api/support/deleted-records', { params }).subscribe({
      next: r => { this.records = r; this.loading = false; },
      error: e => { this.error = e?.error?.message || 'Não foi possível carregar os registros excluídos.'; this.loading = false; }
    });
  }
  changeType(type: DeletedRecordType): void { this.success = ''; this.selectedType = type; this.search = ''; this.load(); }
  restore(record: DeletedRecord): void {
    if (!confirm(`Recuperar "${record.title}"?`)) return;
    this.restoringId = record.id; this.error = ''; this.success = '';
    this.http.patch<void>(`/api/support/deleted-records/${record.type}/${record.id}/restore`, {}).subscribe({
      next: () => { this.success = 'Registro recuperado com sucesso.'; this.restoringId = null; this.load(); },
      error: e => { this.error = e?.error?.message || 'Não foi possível recuperar o registro.'; this.restoringId = null; }
    });
  }
  typeLabel(type: DeletedRecordType): string { return this.types.find(t => t.value === type)?.label ?? type; }
}
