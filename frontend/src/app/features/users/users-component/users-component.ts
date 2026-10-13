import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged, finalize, Subject } from 'rxjs';
import { NotificationService } from '../../../core/services/notification-service';
import { UserService } from '../../../core/services/user-service';
import { User, UserCreateRequest, UserUpdateRequest } from '../../models/user';

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
  private readonly destroyRef = inject(DestroyRef);
  private readonly userSearch = new Subject<string>();

  users = signal<User[]>([]);
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal('');

  currentPage = signal(0);
  pageSize = signal(10);
  totalElements = signal(0);
  totalPages = computed(() => Math.ceil(this.totalElements() / this.pageSize()));

  showFormModal = signal(false);
  showDeleteModal = signal(false);
  editingUser = signal<User | null>(null);
  deletingUser = signal<User | null>(null);
  isSaving = signal(false);
  isDeleting = signal(false);

  form: FormGroup = this.fb.group({
    name: ['', [Validators.required]],
    username: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    role: ['ROLE_USER', Validators.required],
  });

  ngOnInit(): void {
    this.configureSearch();
    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.userService
      .findAll(this.currentPage(), this.pageSize(), this.searchTerm())
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (page) => {
          this.users.set(page.content ?? []);
          this.totalElements.set(page.totalElements ?? 0);

          if (this.currentPage() > 0 && this.users().length === 0) {
            this.currentPage.update(pageNumber => pageNumber - 1);
            this.loadUsers();
          }
        },
        error: () => {
          this.users.set([]);
          this.totalElements.set(0);
          this.errorMessage.set('Não foi possível carregar os usuários.');
        },
      });
  }

  private configureSearch(): void {
    this.userSearch
      .pipe(
        debounceTime(350),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        this.currentPage.set(0);
        this.loadUsers();
      });
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
    this.userSearch.next(value.trim());
  }

  previousPage(): void {
    if (this.currentPage() <= 0) return;
    this.currentPage.update(page => page - 1);
    this.loadUsers();
  }

  nextPage(): void {
    if (this.currentPage() + 1 >= this.totalPages()) return;
    this.currentPage.update(page => page + 1);
    this.loadUsers();
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
    this.form.reset({
      name: user.fullName,
      username: user.username,
      email: user.email,
      password: '',
      role: user.role,
    });
    this.form.get('username')?.disable();
    this.form.get('password')?.clearValidators();
    this.form.get('password')?.updateValueAndValidity();
    this.showFormModal.set(true);
  }

  closeFormModal(): void {
    if (this.isSaving()) return;
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
    const editing = this.editingUser();

    if (editing) {
      const payload: UserUpdateRequest = {
        name: formVal.name,
        email: formVal.email,
        role: formVal.role,
      };

      this.userService.update(editing.id, payload)
        .pipe(finalize(() => this.isSaving.set(false)))
        .subscribe({
          next: () => {
            this.notification.success('Usuário atualizado com sucesso!');
            this.showFormModal.set(false);
            this.loadUsers();
          },
          error: () => this.notification.error('Erro ao atualizar usuário.'),
        });
      return;
    }

    const payload: UserCreateRequest = {
      username: formVal.username,
      name: formVal.name,
      fullName: formVal.name,
      email: formVal.email,
      password: formVal.password,
      role: formVal.role,
    };

    this.userService.create(payload)
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: () => {
          this.notification.success('Usuário criado com sucesso!');
          this.showFormModal.set(false);
          this.currentPage.set(0);
          this.loadUsers();
        },
        error: () => this.notification.error('Erro ao criar usuário.'),
      });
  }

  confirmDelete(user: User): void {
    this.deletingUser.set(user);
    this.showDeleteModal.set(true);
  }

  closeDeleteModal(): void {
    if (this.isDeleting()) return;
    this.showDeleteModal.set(false);
    this.deletingUser.set(null);
  }

  deleteUser(): void {
    const user = this.deletingUser();
    if (!user || this.isDeleting()) return;

    this.isDeleting.set(true);
    this.userService.delete(user.id)
      .pipe(finalize(() => this.isDeleting.set(false)))
      .subscribe({
        next: () => {
          this.notification.success('Usuário excluído com sucesso!');
          this.showDeleteModal.set(false);
          this.deletingUser.set(null);
          this.loadUsers();
        },
        error: () => this.notification.error('Erro ao excluir usuário.'),
      });
  }
}
