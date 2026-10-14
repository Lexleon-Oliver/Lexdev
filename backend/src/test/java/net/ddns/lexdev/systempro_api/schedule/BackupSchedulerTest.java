package net.ddns.lexdev.systempro_api.schedule;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.config.BackupSchedulingProperties;
import net.ddns.lexdev.systempro_api.dto.backup.BackupResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.service.backup.BackupRetentionService;
import net.ddns.lexdev.systempro_api.service.backup.BackupService;

class BackupSchedulerTest {

    @Test
    void deveExecutarRetencaoSomenteDepoisDeBackupAutomaticoConcluido() {
        BackupService backupService = mock(BackupService.class);
        BackupRetentionService retention = mock(BackupRetentionService.class);
        when(backupService.generateScheduled()).thenReturn(Optional.of(
            new BackupResponseDto("systempro-backup-20261014T050000Z.spbackup", 100L, Instant.parse("2026-10-14T05:00:00Z"))));

        scheduler(true, backupService, retention).runScheduledBackup();

        verify(backupService).generateScheduled();
        verify(retention).applyScheduledRetention();
    }

    @Test
    void naoDeveExecutarRetencaoQuandoBackupEstiverOcupadoOuFalhar() {
        BackupService busyService = mock(BackupService.class);
        BackupRetentionService busyRetention = mock(BackupRetentionService.class);
        when(busyService.generateScheduled()).thenReturn(Optional.empty());
        scheduler(true, busyService, busyRetention).runScheduledBackup();
        verifyNoInteractions(busyRetention);

        BackupService failingService = mock(BackupService.class);
        BackupRetentionService failingRetention = mock(BackupRetentionService.class);
        when(failingService.generateScheduled()).thenThrow(new BusinessException("falha"));
        scheduler(true, failingService, failingRetention).runScheduledBackup();
        verifyNoInteractions(failingRetention);
    }

    @Test
    void naoDeveExecutarQuandoSchedulerEstiverDesabilitado() {
        BackupService backupService = mock(BackupService.class);
        BackupRetentionService retention = mock(BackupRetentionService.class);
        scheduler(false, backupService, retention).runScheduledBackup();
        verifyNoInteractions(backupService, retention);
    }

    private BackupScheduler scheduler(boolean enabled, BackupService backupService, BackupRetentionService retention) {
        return new BackupScheduler(backupService, retention,
            new BackupSchedulingProperties(enabled, "0 0 2 * * *", "America/Sao_Paulo", 30));
    }
}