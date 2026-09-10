import { CommonModule } from '@angular/common';
import { Component, computed, OnInit, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { User } from '../../models/user';
import { HttpClient } from '@angular/common/http';
import { NotificationService } from '../../../core/services/notification-service';
import { UserService } from '../../../core/services/user-service';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-users-component',
  styleUrl: './users-component.scss',
  templateUrl: './users-component.html',
})
export class UsersComponent implements OnInit {
  // Estado
  users = signal<User[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  // Signal computado para filtragem
  filteredUsers = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const currentUsers = this.users();

    // Segurança contra valores que não sejam arrays
    if (!Array.isArray(currentUsers)) return [];

    if (!term) return currentUsers;

    return currentUsers.filter(user =>
      user.fullName?.toLowerCase().includes(term) ||
      user.email?.toLowerCase().includes(term) ||
      user.username?.toLowerCase().includes(term)
    );
  });

  // Controle dos modais
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingUser = signal<User | null>(null);
  deletingUser = signal<User | null>(null);
  isSaving = signal(false);

  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private notification: NotificationService,
    private userService: UserService,
  ) {
    this.form = this.fb.group({
      name: ['', [Validators.required]],
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      role: ['ROLE_USER', Validators.required],
      active: [true]
    });
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.userService.getAll().subscribe({
      next: (pageData) => {
        // Pega a lista do atributo .content do Spring Page
        this.users.set(pageData.content || []);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Erro ao carregar usuários.');
        this.isLoading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.editingUser.set(null);
    this.form.reset({ role: 'ROLE_USER', active: true });

    // Habilita username e ativa a validação de senha para criação
    this.form.get('username')?.enable();
    this.form.get('password')?.setValidators([Validators.required, Validators.minLength(6)]);
    this.form.get('password')?.updateValueAndValidity();

    this.showFormModal.set(true);
  }

  openEditModal(user: User): void {
    this.editingUser.set(user);
    this.form.patchValue({
      name: user.fullName,
      username: user.username,
      email: user.email,
      role: user.role,
      active: user.active
    });
    // Desabilita username e remove validação de senha na edição
    this.form.get('username')?.disable();
    this.form.get('password')?.clearValidators();
    this.form.get('password')?.updateValueAndValidity();

    // Desabilita o campo de usuário na edição (ficará readonly no HTML)
    this.form.get('username')?.disable();
    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    this.showFormModal.set(false);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving.set(true);
    const formVal = this.form.getRawValue(); // Pega inclusive campos desabilitados se necessário
    if (this.editingUser()) {
      // EDIÇÃO: Envia apenas os campos do UserUpdateDto
      const id = this.editingUser()!.id;
      const updatePayload = {
        name: formVal.name,
        email: formVal.email,
        role: formVal.role,
        active: formVal.active
      };

      this.userService.update(id, updatePayload).subscribe({
        next: (updated) => {
          this.users.update(list => list.map(u => u.id === id ? updated : u));
          this.notification.success('Usuário atualizado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.isSaving.set(false);
        }
      });
    } else {
      // CRIAÇÃO: Envia todos os campos exigidos pelo UserCreateDto
      const createPayload = {
        username: formVal.username,
        name: formVal.name,
        fullName: formVal.name, // Preenche o fullName exigido pelo DTO Java
        email: formVal.email,
        password: formVal.password,
        role: formVal.role
      };

      this.userService.create(createPayload).subscribe({
        next: (created) => {
          this.users.update(list => [...list, created]);
          this.notification.success('Usuário criado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: (err) => {
          this.isSaving.set(false);
        }
      });
    }
  }
  confirmDelete(user: User): void {
    this.deletingUser.set(user);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deletingUser.set(null);
  }

  deleteUser(): void {
    const user = this.deletingUser();
    if (!user) return;

    this.userService.delete(user.id).subscribe({
      next: () => {
        this.users.update(list => list.filter(u => u.id !== user.id));
        this.notification.success('Usuário excluído com sucesso!');
        this.closeDeleteModal();
      },
      error: () => {
        this.notification.error('Erro ao excluir usuário.');
      }
    });
  }
}
