import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { cpfCnpjValidator } from '../../../core/validators/cpf-cnpj.validator';
import { Client } from '../../models/client';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-clients-component',
  styleUrl: './clients-component.scss',
  templateUrl: './clients-component.html',
})
export class ClientsComponent implements OnInit {
  // Estado
  clients = signal<Client[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  // Signal computado para filtragem de clientes
  filteredClients = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const currentClients = this.clients();

    // Segurança contra valores que não sejam arrays
    if (!Array.isArray(currentClients)) return [];

    if (!term) return currentClients;

    return currentClients.filter(client =>
      client.name?.toLowerCase().includes(term) ||
      client.email?.toLowerCase().includes(term) ||
      client.phone?.includes(term)
    );
  });

  // Modais
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingClient = signal<Client | null>(null);
  deletingClient = signal<Client | null>(null);
  isSaving = signal(false);

  // Controle de abas
  activeTab = signal<'identification' | 'contact' | 'address' | 'settings'>('identification');

  // Lista de UF
  ufList = [
    'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA',
    'PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'
  ];

  form: FormGroup;

  constructor(
    private http: HttpClient,
    private fb: FormBuilder,
    private notification: NotificationService
  ) {
    this.form = this.fb.group({
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
      active: [true]
    });
  }

  ngOnInit(): void {
    this.loadClients();
  }

  // ===== CRUD =====
  loadClients(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.http.get<Client[]>('/api/clients').subscribe({
      next: (data) => {
        this.clients.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Erro ao carregar clientes.');
        this.isLoading.set(false);
      }
    });
  }

  // ===== Formulário =====
  openCreateModal(): void {
    this.editingClient.set(null);
    this.form.reset({ tipoPessoa: 'PF', active: true });
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
      active: client.active
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

    if (this.editingClient()) {
      const id = this.editingClient()!.id;
      this.http.put<Client>(`/api/clients/${id}`, payload).subscribe({
        next: (updated) => {
          this.clients.update(list => list.map(c => c.id === id ? updated : c));
          this.notification.success('Cliente atualizado!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao atualizar.');
          this.isSaving.set(false);
        }
      });
    } else {
      this.http.post<Client>('/api/clients', payload).subscribe({
        next: (created) => {
          this.clients.update(list => [...list, created]);
          this.notification.success('Cliente criado!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao criar.');
          this.isSaving.set(false);
        }
      });
    }
  }

  formatCpfCnpj(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, ''); // Remove não dígitos

    if (value.length > 14) value = value.substring(0, 14);

    if (value.length <= 11) {
      // Máscara CPF: 000.000.000-00
      value = value
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d)/, '$1.$2')
        .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
    } else {
      // Máscara CNPJ: 00.000.000/0000-00
      value = value
        .replace(/^(\d{2})(\d)/, '$1.$2')
        .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d)/, '.$1/$2')
        .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
    }

    this.form.get('cpfCnpj')?.setValue(value, { emitEvent: false });
  }

  // Máscara de Telefone: (00) 0000-0000 ou (00) 00000-0000
  formatPhone(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, ''); // Remove não dígitos

    if (value.length > 11) value = value.substring(0, 11);

    if (value.length <= 10) {
      value = value
        .replace(/^(\d{2})(\d)/, '($1) $2')
        .replace(/(\d{4})(\d)/, '$1-$2');
    } else {
      value = value
        .replace(/^(\d{2})(\d)/, '($1) $2')
        .replace(/(\d{5})(\d)/, '$1-$2');
    }

    this.form.get('phone')?.setValue(value, { emitEvent: false });
  }

  // Máscara de CEP: 00000-000
  formatCep(event: Event): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, ''); // Remove não dígitos

    if (value.length > 8) value = value.substring(0, 8);

    value = value.replace(/^(\d{5})(\d)/, '$1-$2');

    this.form.get('cep')?.setValue(value, { emitEvent: false });
  }

  // ===== Exclusão =====
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
    if (!client) return;
    this.http.delete(`/api/clients/${client.id}`).subscribe({
      next: () => {
        this.clients.update(list => list.filter(c => c.id !== client.id));
        this.notification.success('Cliente excluído!');
        this.closeDeleteModal();
      },
      error: () => this.notification.error('Erro ao excluir.')
    });
  }

  // ===== Toggle Active =====
  toggleActive(): void {
    this.form.patchValue({ active: !this.form.get('active')?.value });
  }

  // ===== ViaCEP =====
  buscarCep(): void {
    const cep = this.form.get('cep')?.value?.replace(/\D/g, '');
    if (cep?.length !== 8) return;

    this.http.get(`https://viacep.com.br/ws/${cep}/json/`).subscribe({
      next: (data: any) => {
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

