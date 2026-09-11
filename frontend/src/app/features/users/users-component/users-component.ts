import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
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
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly notification = inject(NotificationService);

  // Estado
  users = signal<User[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  // Paginação
  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);

  filteredUsers = computed(() => {
    const term = this.searchTerm().toLowerCase();
    const currentUsers = this.users();

    if (!Array.isArray(currentUsers)) return [];
    if (!term) return currentUsers;

    return currentUsers.filter(user =>
      user.fullName?.toLowerCase().includes(term) ||
      user.email?.toLowerCase().includes(term) ||
      user.username?.toLowerCase().includes(term)
    );
  });

  // Modais e UI
  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingUser = signal<User | null>(null);
  deletingUser = signal<User | null>(null);
  isSaving = signal(false);

  form: FormGroup = this.fb.group({
    name: ['', [Validators.required]],
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    role: ['ROLE_USER', Validators.required],
  });

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.userService.findAll(this.currentPage(), this.pageSize()).subscribe({
      next: (page) => {
        this.users.set(page.content || []);
        this.totalElements.set(page.totalElements);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Erro ao carregar usuários.');
        this.isLoading.set(false);
      }
    });
  }

  openCreateModal(): void {
    this.editingUser.set(null);
    this.form.reset({ role: 'ROLE_USER' });

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
      role: user.role
    });

    this.form.get('username')?.disable();
    this.form.get('password')?.clearValidators();
    this.form.get('password')?.updateValueAndValidity();

    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    this.showFormModal.set(false);
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notification.show('Preencha os campos corretamente.', 'warning');
      return;
    }

    this.isSaving.set(true);
    const formVal = this.form.getRawValue();

    if (this.editingUser()) {
      const id = this.editingUser()!.id;
      const updatePayload = {
        name: formVal.name,
        email: formVal.email,
        role: formVal.role
      };

      this.userService.update(id, updatePayload).subscribe({
        next: (updated) => {
          this.users.update(list => list.map(u => u.id === id ? updated : u));
          this.notification.success('Usuário atualizado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao atualizar usuário.');
          this.isSaving.set(false);
        }
      });
    } else {
      const createPayload = {
        username: formVal.username,
        name: formVal.name,
        fullName: formVal.name,
        email: formVal.email,
        password: formVal.password,
        role: formVal.role
      };

      this.userService.create(createPayload).subscribe({
        next: (created) => {
          this.users.update(list => [created, ...list]);
          this.notification.success('Usuário criado com sucesso!');
          this.isSaving.set(false);
          this.closeFormModal();
        },
        error: () => {
          this.notification.error('Erro ao criar usuário.');
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
      error: () => this.notification.error('Erro ao excluir usuário.')
    });
  }
}
