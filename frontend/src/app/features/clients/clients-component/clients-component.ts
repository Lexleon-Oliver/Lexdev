import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificationService } from '../../../core/services/notification-service';
import { cpfCnpjValidator } from '../../../core/validators/cpf-cnpj.validator';
import { Client } from '../../models/client';
import { ClientService } from '../../../core/services/client-service';
import { CepService } from '../../../core/services/cep-service';
import { PersonContactResponseDto } from '../../models/person-contact-response-dto';
import { PersonAddressResponseDto } from '../../models/person-address-response-dto';
import { TipoContato } from '../../models/tipo-contato';


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
    const term = this.searchTerm().trim();
    const normalizedTerm = this.normalizeSearchValue(term);

    const list = this.clients();

    if (!Array.isArray(list)) {
      return [];
    }

    if (!term) {
      return list;
    }

    return list.filter((client) => {

      const name = this.normalizeSearchValue(
        client.person?.name ?? ''
      );

      const doc = this.normalizeSearchValue(
        client.person?.cpfCnpj ?? ''
      );

      const emails = (client.contacts ?? [])
        .filter((c) => c.type === 'EMAIL')
        .map((c) =>
          this.normalizeSearchValue(c.value ?? '')
        )
        .join(' ');

      const phones = (client.contacts ?? [])
        .filter((c) => c.type !== 'EMAIL')
        .map((c) =>
          this.normalizeSearchValue(c.value ?? '')
        )
        .join(' ');

      return (
        name.includes(normalizedTerm) ||
        doc.includes(normalizedTerm) ||
        emails.includes(normalizedTerm) ||
        phones.includes(normalizedTerm)
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

    if (!preferred?.value) return '—';

    // Reutiliza o applyPhoneMask que já existe no seu componente
    return this.applyPhoneMask(preferred.value);
  }

  /* ==================== FormArray factories ==================== */

  private createContactGroup(contact?: PersonContactResponseDto): FormGroup {
    // Se existir contato e não for e-mail, aplica a máscara no valor
    let maskedValue = contact?.value ?? '';
    if (contact && contact.type !== 'EMAIL') {
      maskedValue = this.applyPhoneMask(maskedValue);
    }

    return this.fb.group({
      id: [contact?.id ?? null],
      type: [contact?.type ?? 'EMAIL', Validators.required],
      value: [maskedValue, Validators.required],
      description: [contact?.description ?? ''],
      principal: [contact?.principal ?? false],
    });
  }

  private createAddressGroup(address?: PersonAddressResponseDto): FormGroup {
    // Aplica a máscara no CEP
    const maskedCep = this.applyCepMask(address?.cep);

    return this.fb.group({
      id: [address?.id ?? null],
      type: [address?.type ?? 'RESIDENCIAL', Validators.required],
      cep: [maskedCep],
      logradouro: [address?.logradouro ?? ''],
      numero: [address?.numero ?? '', Validators.required],
      complemento: [address?.complemento ?? ''],
      bairro: [address?.bairro ?? ''],
      cidade: [address?.cidade ?? ''],
      uf: [address?.uf ?? ''],
      principal: [address?.principal ?? false],
    });
  }

  addContact(contact?: PersonContactResponseDto): void {
    this.contacts.push(this.createContactGroup(contact));
  }

  removeContact(index: number): void {
    this.contacts.removeAt(index);
  }

  addAddress(address?: PersonAddressResponseDto): void {
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
      cpfCnpj: this.applyCpfCnpjMask(
        client.person.cpfCnpj,
        client.person.tipoPessoa
      ),
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
        // Remove pontuações do CPF/CNPJ antes de enviar
        cpfCnpj: v.cpfCnpj ? v.cpfCnpj.replace(/\D/g, '') : '',
      },
      individual: isPF
      ? {
          id: editing?.individual?.id,
          // Remove espaços, pontos e hífens, mantendo letras e números em maiúsculo
          rg: v.rg ? v.rg.replace(/[^a-zA-Z0-9]/g, '').toUpperCase() : '',
        }
      : null,
      legalEntity: !isPF
        ? {
            id: editing?.legalEntity?.id,
            nomeFantasia: v.nomeFantasia,
            inscricaoEstadual: v.inscricaoEstadual,
          }
        : null,
      contacts: (v.contacts as PersonContactResponseDto[]).map((c) => ({
        id: c.id ?? undefined,
        type: c.type,
        // Se NÃO for e-mail, remove parênteses, traços e espaços do telefone/celular
        value: c.type !== 'EMAIL' && c.value ? c.value.replace(/\D/g, '') : c.value,
        description: c.description,
        principal: c.principal,
      })),
      addresses: (v.addresses as PersonAddressResponseDto[]).map((a) => ({
        id: a.id ?? undefined,
        type: a.type,
        // Remove hífen do CEP
        cep: a.cep ? a.cep.replace(/\D/g, '') : '',
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
    const tipoPessoa =
      this.form.get('tipoPessoa')?.value;

    const maskedValue =
      this.applyCpfCnpjMask(
        input.value,
        tipoPessoa
      );
    this.form.get('cpfCnpj')?.setValue(maskedValue, { emitEvent: false });
  }

  onContactValueInput(event: Event, index: number): void {
    const group = this.contacts.at(index);
    const type = group.get('type')?.value;
    const input = event.target as HTMLInputElement;

    if (type === 'EMAIL') {
      group.get('value')?.setValue(input.value, { emitEvent: false });
      return;
    }

    const maskedValue = this.applyPhoneMask(input.value);
    group.get('value')?.setValue(maskedValue, { emitEvent: false });
  }

  onContactTypeChange(
    index: number
  ): void {

    // Ao trocar o tipo, limpa o valor
    // para impedir que um e-mail vire telefone
    // ou que um telefone seja tratado como e-mail.
    this.contacts
      .at(index)
      .get('value')
      ?.setValue('');
  }

  formatCep(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const maskedValue = this.applyCepMask(input.value);
    this.addresses.at(index).get('cep')?.setValue(maskedValue, { emitEvent: false });
  }

  formatRg(event: Event): void {
    const input = event.target as HTMLInputElement;
    // Mantém apenas letras e números, convertendo para maiúsculo
    const value = input.value.replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
    this.form.get('rg')?.setValue(value, { emitEvent: false });
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

  /* ==================== Utilitários de Máscara ==================== */

  applyCpfCnpjMask(
    value: string | undefined | null,
    tipoPessoa?: 'PF' | 'PJ' | string
  ): string {

    if (!value) {
      return '';
    }

    let str =
      value.replace(/\D/g, '');

    const isPJ =
      tipoPessoa === 'PJ'
        ? true
        : tipoPessoa === 'PF'
          ? false
          : str.length > 11;

    if (isPJ) {

      // CNPJ possui no máximo 14 dígitos
      if (str.length > 14) {
        str = str.substring(0, 14);
      }

      return str

        // 12
        // 12.3
        .replace(
          /^(\d{2})(\d)/,
          '$1.$2'
        )

        // 12.345.6
        .replace(
          /^(\d{2})\.(\d{3})(\d)/,
          '$1.$2.$3'
        )

        // 12.345.678/...
        .replace(
          /\.(\d{3})(\d)/,
          '.$1/$2'
        )

        // 12.345.678/0001-...
        .replace(
          /(\d{4})(\d{1,2})$/,
          '$1-$2'
        );
    }
    // CPF possui no máximo 11 dígitos
    if (str.length > 11) {
      str = str.substring(0, 11);
    }
    return str
      // 123
      // 123.4
      .replace(
        /(\d{3})(\d)/,
        '$1.$2'
      )
      // 123.456.7
      .replace(
        /(\d{3})(\d)/,
        '$1.$2'
      )
      // 123.456.789-...
      .replace(
        /(\d{3})(\d{1,2})$/,
        '$1-$2'
      );
  }


  private applyPhoneMask(value: string | undefined | null): string {
    if (!value) return '';
    let str = value.replace(/\D/g, '');
    if (str.length > 11) str = str.substring(0, 11);

    if (str.length <= 10) {
      return str
        .replace(/^(\d{2})(\d)/, '($1) $2')
        .replace(/(\d{4})(\d)/, '$1-$2');
    } else {
      return str
        .replace(/^(\d{2})(\d)/, '($1) $2')
        .replace(/(\d{5})(\d)/, '$1-$2');
    }
  }

  private applyCepMask(value: string | undefined | null): string {
    if (!value) return '';
    let str = value.replace(/\D/g, '');
    if (str.length > 8) str = str.substring(0, 8);
    return str.replace(/^(\d{5})(\d)/, '$1-$2');
  }

  private normalizeSearchValue(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  }
}
