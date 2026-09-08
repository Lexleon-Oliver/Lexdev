import { Injectable, Service, signal } from '@angular/core';
import { Toast } from '../../features/models/toast';

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private toastsSignal = signal<Toast[]>([]);
  public toasts = this.toastsSignal.asReadonly();

  show(message: string, type: 'success' | 'error' | 'info' | 'warning' = 'info', duration = 4500) {
    const id = Date.now();
    const newToast: Toast = { id, message, type };

    this.toastsSignal.update(toasts => [...toasts, newToast]);

    setTimeout(() => {
      this.remove(id);
    }, duration);
  }

  error(message: string) {
    this.show(message, 'error');
  }

  success(message: string) {
    this.show(message, 'success');
  }

  remove(id: number) {
    this.toastsSignal.update(toasts => toasts.filter(t => t.id !== id));
  }
}
