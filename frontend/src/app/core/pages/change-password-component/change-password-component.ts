import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { NotificationService } from '../../services/notification-service';

@Component({
  imports: [CommonModule, ReactiveFormsModule],
  selector: 'app-change-password-component',
  styleUrl: './change-password-component.scss',
  templateUrl: './change-password-component.html',
})
export class ChangePasswordComponent {
  form: FormGroup;
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private router: Router,
    private notification: NotificationService
  ) {
    this.form = this.fb.group({
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    }, { validators: this.passwordMatchValidator });
  }

  passwordMatchValidator(group: FormGroup) {
    const newPassword = group.get('newPassword')?.value;
    const confirmPassword = group.get('confirmPassword')?.value;
    return newPassword === confirmPassword ? null : { mismatch: true };
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const payload = {
      currentPassword: this.form.value.currentPassword,
      newPassword: this.form.value.newPassword
    };

    this.http.patch('/api/users/me/password', payload).subscribe({
      next: () => {
        this.notification.success('Senha alterada com sucesso!');
        this.isLoading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        if (err.status === 400) {
          this.errorMessage.set('Senha atual incorreta ou nova senha inválida.');
        } else {
          this.errorMessage.set('Erro ao alterar senha. Tente novamente.');
        }
        this.notification.error(this.errorMessage() || 'Erro ao alterar senha.');
      }
    });
  }
}
