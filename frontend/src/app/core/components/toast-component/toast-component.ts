import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { NotificationService } from '../../services/notification-service';

@Component({
  imports: [CommonModule],
  selector: 'app-toast-component',
  styleUrl: './toast-component.scss',
  templateUrl: './toast-component.html',
})
export class ToastComponent {
  notificationService = inject(NotificationService);

  // Remove manualmente um toast
  removeToast(id: number) {
    this.notificationService.remove(id);
  }
}
