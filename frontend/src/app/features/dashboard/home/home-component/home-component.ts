import { Component, inject } from '@angular/core';
import { AuthService } from '../../../../core/services/auth-service';
import { NotificationService } from '../../../../core/services/notification-service';

@Component({
  imports: [],
  selector: 'app-home-component',
  styleUrl: './home-component.scss',
  templateUrl: './home-component.html',
})
export class HomeComponent {
  authService = inject(AuthService);

  private notificationService = inject(NotificationService);

  testSuccess() {
    this.notificationService.success('Usuário salvo com sucesso!');
  }

  testError() {
    this.notificationService.error('Erro de teste: Não foi possível conectar ao servidor.');
  }

  testWarning() {
    this.notificationService.show('Atenção: A sua sessão expira em 5 minutos.', 'warning');
  }
}
