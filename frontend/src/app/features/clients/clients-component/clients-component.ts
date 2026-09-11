import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { cpfCnpjValidator } from '../../../core/validators/cpf-cnpj.validator';
import { Client } from '../../models/client';
import { ClientService } from '../../../core/services/client-service';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-clients-component',
  styleUrl: './clients-component.scss',
  templateUrl: './clients-component.html',
})
export class ClientsComponent implements OnInit {
  private readonly clientService = inject(ClientService);
  private readonly fb = inject(FormBuilder);
  private readonly notification = inject(NotificationService);

  // Estado
  clients = signal<Client[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  // Paginação
  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  filteredClients = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const currentClients = this.clients();

    if (!Array.isArray(currentClients)) return [];
    if (!term) return currentClients;

    return currentClients.filter(client =>
      client.name?.toLowerCase().includes(term) ||
      client.email?.toLowerCase().includes(term) ||
      client.phone?.includes(term)
    );
  });

  // Modais e UI
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingClient = signal<Client | null>(null);
  deletingClient = signal<Client | null>(null);
  isSaving = signal(false);
  activeTab = signal<'identification' | 'contact' | 'address' | 'settings'>('identification');

  readonly ufList = [
    'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA',
    'PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
  ];

  form: FormGroup = this.fb.group({
    tipoPessoa: ['PF', Validators.required],
    name: ['', Validators.required],
    nomeFantasia: [''],
    cpfCnpj: ['', [Validators.required, cpfCnpjValidator()]],
    rgIe: [''],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.required],
    cep: [''],
    logradouro: [''],
    numero: ['', Validators.required],
    complemento: [''],
    bairro: [''],
    cidade: [''],
    uf: [''],
  });

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.clientService.findAll(this.currentPage(), this.pageSize()).subscribe({
      next: (page) => {
        this.clients.set(page.content || []);
        this.totalElements.set(page.totalElements);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Erro ao carregar clientes.');
        this.isLoading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.editingClient.set(null);
    this.form.reset({ tipoPessoa: 'PF' });
    this.activeTab.set('identification');
    this.showFormModal.set(true);
  }

  openEditModal(client: Client): void {
    this.editingClient.set(client);
    this.form.patchValue({
      tipoPessoa: client.tipoPessoa,
      name: client.name,
      nomeFantasia: client.nomeFantasia || '',
      cpfCnpj: client.cpfCnpj,
      rgIe: client.rgIe || '',
      email: client.email,
      phone: client.phone,
      cep: client.cep || '',
      logradouro: client.logradouro || '',
      numero: client.numero || '',
      complemento: client.complemento || '',
      bairro: client.bairro || '',
      cidade: client.cidade || '',
      uf: client.uf || '',
    });
    this.activeTab.set('identification');
    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    this.showFormModal.set(false);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show('Preencha todos os campos obrigatórios.', 'warning');
      return;
    }

    this.isSaving.set(true);
    const payload = this.form.value;
    const editing = this.editingClient();

    if (editing?.id) {
      this.clientService.update(editing.id, payload).subscribe({
        next: (updated) => {
          this.clients.update(list => list.map(c => c.id === editing.id ? updated : c));
          this.notification.success('Cliente atualizado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao atualizar cliente.');
          this.isSaving.set(false);
        }
      });
    } else {
      this.clientService.create(payload).subscribe({
        next: (created) => {
          this.clients.update(list => [created, ...list]);
          this.notification.success('Cliente criado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao criar cliente.');
          this.isSaving.set(false);
        }
      });
    }
  }

  confirmDelete(client: Client): void {
    this.deletingClient.set(client);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deletingClient.set(null);
  }

  deleteClient(): void {
    const client = this.deletingClient();
    if (!client?.id) return;

    this.clientService.delete(client.id).subscribe({
      next: () => {
        this.clients.update(list => list.filter(c => c.id !== client.id));
        this.notification.success('Cliente excluído com sucesso!');
        this.closeDeleteModal();
      },
      error: () => this.notification.error('Erro ao excluir cliente.')
    });
  }

  formatCpfCnpj(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 14) value = value.substring(0, 14);

    if (value.length <= 11) {
      value = value
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
    } else {
      value = value
        .replace(/^(\d{2})(\d)/, '$1.$2')
        .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d)/, '.$1/$2')
        .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
    }
    this.form.get('cpfCnpj')?.setValue(value, { emitEvent: false });
  }

  formatPhone(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 11) value = value.substring(0, 11);

    if (value.length <= 10) {
      value = value.replace(/^(\d{2})(\d)/, '($1) $2').replace(/(\d{4})(\d)/, '$1-$2');
    } else {
      value = value.replace(/^(\d{2})(\d)/, '($1) $2').replace(/(\d{5})(\d)/, '$1-$2');
    }
    this.form.get('phone')?.setValue(value, { emitEvent: false });
  }

  formatCep(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 8) value = value.substring(0, 8);
    value = value.replace(/^(\d{5})(\d)/, '$1-$2');
    this.form.get('cep')?.setValue(value, { emitEvent: false });
  }

  buscarCep(): void {
    const cep = this.form.get('cep')?.value;
    if (!cep || cep.replace(/\D/g, '').length !== 8) return;

    this.clientService.getAddressByCep(cep).subscribe({
      next: (data) => {
        if (!data.erro) {
          this.form.patchValue({
            logradouro: data.logradouro || '',
            bairro: data.bairro || '',
            cidade: data.localidade || '',
            uf: data.uf || ''
          });
        } else {
          this.notification.show('CEP não encontrado.', 'warning');
        }
      },
      error: () => this.notification.error('Erro ao buscar CEP.')
    });
  }
}
