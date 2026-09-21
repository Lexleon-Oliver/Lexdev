import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { cpfCnpjValidator } from '../../../core/validators/cpf-cnpj.validator';
import { Client } from '../../models/client';
import { ClientService } from '../../../core/services/client-service';
import { CepService } from '../../../core/services/cep-service';
import { PersonContact } from '../../models/person-contact';
import { PersonAddress } from '../../models/person-address';
import { ContactType } from '../../models/contact-type';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-clients-component',
  styleUrl: './clients-component.scss',
  templateUrl: './clients-component.html',
})
export class ClientsComponent implements OnInit {
  private readonly clientService = inject(ClientService);
  private readonly cepService = inject(CepService);
  private readonly fb = inject(FormBuilder);
  private readonly notification = inject(NotificationService);

  // Estado
  clients = signal<Client[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');
  searchingCepIndex = signal<number | null>(null);

  // Paginação
  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  filteredClients = computed(() => {
    const term = this.searchTerm().toLowerCase().trim();
    const list = this.clients();

    if (!Array.isArray(list)) return [];
    if (!term) return list;

    return list.filter((client) => {
      const name = client.person?.name?.toLowerCase() ?? '';
      const doc = client.person?.cpfCnpj?.toLowerCase() ?? '';
      const emails = (client.contacts ?? [])
        .filter((c) => c.type === 'EMAIL')
        .map((c) => c.value.toLowerCase())
        .join(' ');
      const phones = (client.contacts ?? [])
        .filter((c) => c.type !== 'EMAIL')
        .map((c) => c.value)
        .join(' ');

      return (
        name.includes(term) ||
        doc.includes(term) ||
        emails.includes(term) ||
        phones.includes(term)
      );
    });
  });

  // Modais e UI
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingClient = signal<Client | null>(null);
  deletingClient = signal<Client | null>(null);
  isSaving = signal(false);
  activeTab = signal<'identification' | 'contact' | 'address'>('identification');

  readonly ufList = [
    'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
    'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
    'SP', 'SE', 'TO',
  ];

  form: FormGroup = this.fb.group({
    tipoPessoa: ['PF', Validators.required],
    name: ['', Validators.required],
    cpfCnpj: ['', [Validators.required, cpfCnpjValidator()]],
    // PF
    rg: [''],
    // PJ
    nomeFantasia: [''],
    inscricaoEstadual: [''],
    // Arrays
    contacts: this.fb.array([]),
    addresses: this.fb.array([]),
  });

  get contacts(): FormArray {
    return this.form.get('contacts') as FormArray;
  }

  get addresses(): FormArray {
    return this.form.get('addresses') as FormArray;
  }

  ngOnInit(): void {
    this.loadClients();
  }

  /* ==================== Carregamento ==================== */

  loadClients(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.clientService.findAll(this.currentPage(), this.pageSize()).subscribe({
      next: (page) => {
        this.clients.set(page.content ?? []);
        this.totalElements.set(page.totalElements);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Erro ao carregar clientes.');
        this.isLoading.set(false);
      },
    });
  }

  /* ==================== Helpers de exibição ==================== */

  getEmail(client: Client): string {
    return client.contacts?.find((c) => c.type === 'EMAIL')?.value ?? '—';
  }

  getPhone(client: Client): string {
    const c = client.contacts ?? [];
    const preferred =
      c.find((x) => x.principal && x.type !== 'EMAIL') ??
      c.find((x) => x.type === 'CELULAR') ??
      c.find((x) => x.type === 'WHATSAPP') ??
      c.find((x) => x.type === 'TELEFONE');
    return preferred?.value ?? '—';
  }

  /* ==================== FormArray factories ==================== */

  private createContactGroup(contact?: PersonContact): FormGroup {
    return this.fb.group({
      id: [contact?.id ?? null],
      type: [contact?.type ?? 'EMAIL', Validators.required],
      value: [contact?.value ?? '', Validators.required],
      description: [contact?.description ?? ''],
      principal: [contact?.principal ?? false],
    });
  }

  private createAddressGroup(address?: PersonAddress): FormGroup {
    return this.fb.group({
      id: [address?.id ?? null],
       type: [address?.type ?? 'RESIDENCIAL', Validators.required],
      cep: [address?.cep ?? ''],
      logradouro: [address?.logradouro ?? ''],
      numero: [address?.numero ?? '', Validators.required],
      complemento: [address?.complemento ?? ''],
      bairro: [address?.bairro ?? ''],
      cidade: [address?.cidade ?? ''],
      uf: [address?.uf ?? ''],
      principal: [address?.principal ?? false],
    });
  }

  addContact(contact?: PersonContact): void {
    this.contacts.push(this.createContactGroup(contact));
  }

  removeContact(index: number): void {
    this.contacts.removeAt(index);
  }

  addAddress(address?: PersonAddress): void {
    this.addresses.push(this.createAddressGroup(address));
  }

  removeAddress(index: number): void {
    this.addresses.removeAt(index);
  }

  /* ==================== Modais ==================== */

  openCreateModal(): void {
    this.editingClient.set(null);
    this.form.reset({ tipoPessoa: 'PF' });
    this.contacts.clear();
    this.addresses.clear();
    this.addContact();
    this.addAddress();
    this.activeTab.set('identification');
    this.showFormModal.set(true);
  }

  openEditModal(client: Client): void {
    this.editingClient.set(client);

    this.form.patchValue({
      tipoPessoa: client.person.tipoPessoa,
      name: client.person.name,
      cpfCnpj: client.person.cpfCnpj,
      rg: client.individual?.rg ?? '',
      nomeFantasia: client.legalEntity?.nomeFantasia ?? '',
      inscricaoEstadual: client.legalEntity?.inscricaoEstadual ?? '',
    });

    this.contacts.clear();
    (client.contacts ?? []).forEach((c) => this.addContact(c));

    this.addresses.clear();
    (client.addresses ?? []).forEach((a) => this.addAddress(a));

    this.activeTab.set('identification');
    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    this.showFormModal.set(false);
  }

  /* ==================== Submit ==================== */

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show('Preencha todos os campos obrigatórios.', 'warning');
      return;
    }

    this.isSaving.set(true);
    const v = this.form.value;
    const isPF = v.tipoPessoa === 'PF';
    const editing = this.editingClient();

    const payload: Partial<Client> = {
      person: {
        id: editing?.person?.id,
        tipoPessoa: v.tipoPessoa,
        name: v.name,
        cpfCnpj: v.cpfCnpj,
      },
      individual: isPF
        ? { id: editing?.individual?.id, rg: v.rg }
        : null,
      legalEntity: !isPF
        ? {
            id: editing?.legalEntity?.id,
            nomeFantasia: v.nomeFantasia,
            inscricaoEstadual: v.inscricaoEstadual,
          }
        : null,
      contacts: (v.contacts as PersonContact[]).map((c) => ({
        id: c.id ?? undefined,
        type: c.type,
        value: c.value,
        description: c.description,
        principal: c.principal,
      })),
      addresses: (v.addresses as PersonAddress[]).map((a) => ({
        id: a.id ?? undefined,
        type: a.type,
        cep: a.cep,
        logradouro: a.logradouro,
        numero: a.numero,
        complemento: a.complemento,
        bairro: a.bairro,
        cidade: a.cidade,
        uf: a.uf,
        principal: a.principal,
      })),
    };

    const request$ = editing?.id
      ? this.clientService.update(editing.id, payload)
      : this.clientService.create(payload);

    request$.subscribe({
      next: (saved) => {
        if (editing?.id) {
          this.clients.update((list) =>
            list.map((c) => (c.id === editing.id ? saved : c))
          );
          this.notification.success('Cliente atualizado com sucesso!');
        } else {
          this.clients.update((list) => [saved, ...list]);
          this.notification.success('Cliente criado com sucesso!');
        }
        this.isSaving.set(false);
        this.closeFormModal();
      },
      error: () => {
        this.notification.error(
          editing?.id ? 'Erro ao atualizar cliente.' : 'Erro ao criar cliente.'
        );
        this.isSaving.set(false);
      },
    });
  }

  /* ==================== Exclusão ==================== */

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
        this.clients.update((list) => list.filter((c) => c.id !== client.id));
        this.notification.success('Cliente excluído com sucesso!');
        this.closeDeleteModal();
      },
      error: () => this.notification.error('Erro ao excluir cliente.'),
    });
  }

  /* ==================== Máscaras ==================== */

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

  onContactTypeChange(index: number): void {
    this.contacts.at(index).get('value')?.setValue('');
  }

  onContactValueInput(event: Event, index: number): void {
    const group = this.contacts.at(index);
    const type = group.get('type')?.value as ContactType;
    const input = event.target as HTMLInputElement;
    let value = input.value;

    if (type === 'EMAIL') {
      group.get('value')?.setValue(value, { emitEvent: false });
      return;
    }

    value = value.replace(/\D/g, '');
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
    group.get('value')?.setValue(value, { emitEvent: false });
  }

  formatCep(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    let value = input.value.replace(/\D/g, '');
    if (value.length > 8) value = value.substring(0, 8);
    value = value.replace(/^(\d{5})(\d)/, '$1-$2');
    this.addresses.at(index).get('cep')?.setValue(value, { emitEvent: false });
  }

  buscarCep(index: number): void {
    const cepCtrl = this.addresses.at(index).get('cep');
    const cep = (cepCtrl?.value ?? '').replace(/\D/g, '');
    if (cep.length !== 8) return;

    this.searchingCepIndex.set(index);
    this.cepService.buscarCep(cep).subscribe({
      next: (data) => {
        this.searchingCepIndex.set(null);
        if (!data.erro) {
          this.addresses.at(index).patchValue({
            logradouro: data.logradouro ?? '',
            bairro: data.bairro ?? '',
            cidade: data.localidade ?? '',
            uf: data.uf ?? '',
          });
        } else {
          this.notification.show('CEP não encontrado.', 'warning');
        }
      },
      error: () => {
        this.searchingCepIndex.set(null);
        this.notification.error('Erro ao buscar CEP.');
      },
    });
  }
}
