import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { BackupService } from '../../../core/services/backup-service';
import { NotificationService } from '../../../core/services/notification-service';
import { BackupResponse } from '../../models/backup';

@Component({
  standalone: true,
  selector: 'app-backup-component',
  imports: [CommonModule],
  templateUrl: './backup-component.html',
  styleUrl: './backup-component.scss',
})
export class BackupComponent implements OnInit {
  private readonly backupService = inject(BackupService);
  private readonly notification = inject(NotificationService);

  readonly backups = signal<BackupResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isCreating = signal(false);
  readonly downloadingFile = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadBackups();
  }

  loadBackups(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.backupService.findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: backups => this.backups.set(this.sortNewestFirst(backups)),
        error: error => {
          this.backups.set([]);
          this.errorMessage.set(
            error?.error?.message ?? 'Não foi possível carregar os backups.'
          );
        },
      });
  }

  createBackup(): void {
    if (this.isCreating()) return;

    this.isCreating.set(true);
    this.backupService.create()
      .pipe(finalize(() => this.isCreating.set(false)))
      .subscribe({
        next: backup => {
          this.backups.update(items =>
            this.sortNewestFirst([
              backup,
              ...items.filter(item => item.fileName !== backup.fileName),
            ])
          );
          this.notification.success('Backup gerado e verificado com sucesso.');
        },
        error: error => {
          this.notification.error(
            error?.error?.message ?? 'Não foi possível gerar o backup.'
          );
        },
      });
  }

  downloadBackup(backup: BackupResponse): void {
    if (this.downloadingFile()) return;

    this.downloadingFile.set(backup.fileName);
    this.backupService.download(backup.fileName)
      .pipe(finalize(() => this.downloadingFile.set(null)))
      .subscribe({
        next: response => {
          if (!response.body) {
            this.notification.error('O arquivo de backup retornado está vazio.');
            return;
          }

          const url = URL.createObjectURL(response.body);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = backup.fileName;
          anchor.style.display = 'none';
          document.body.appendChild(anchor);
          anchor.click();
          anchor.remove();
          URL.revokeObjectURL(url);
        },
        error: () => this.notification.error('Não foi possível baixar o backup.'),
      });
  }

  formatSize(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes < 0) return '—';
    if (bytes < 1024) return `${bytes} B`;

    const units = ['KB', 'MB', 'GB', 'TB'];
    let value = bytes / 1024;
    let unitIndex = 0;

    while (value >= 1024 && unitIndex < units.length - 1) {
      value /= 1024;
      unitIndex++;
    }

    return `${value.toLocaleString('pt-BR', {
      minimumFractionDigits: value < 10 ? 2 : 1,
      maximumFractionDigits: 2,
    })} ${units[unitIndex]}`;
  }

  private sortNewestFirst(backups: BackupResponse[]): BackupResponse[] {
    return [...backups].sort((a, b) =>
      new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );
  }
}
