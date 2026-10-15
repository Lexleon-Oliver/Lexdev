package net.ddns.lexdev.systempro_api.schedule;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.config.BackupSchedulingProperties;
import net.ddns.lexdev.systempro_api.dto.backup.BackupResponseDto;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.service.backup.BackupRetentionService;
import net.ddns.lexdev.systempro_api.service.backup.BackupScheduleDueService;
import net.ddns.lexdev.systempro_api.service.backup.BackupService;

class BackupSchedulerTest {

    @Test
    void deveGerarEReterQuandoBackupAutomaticoEstiverVencido() {
        BackupService backupService = mock(BackupService.class);
        BackupRetentionService retention = mock(BackupRetentionService.class);
        BackupScheduleDueService dueService = mock(BackupScheduleDueService.class);
        when(dueService.isScheduledBackupDue()).thenReturn(true);
        when(backupService.generateScheduled()).thenReturn(Optional.of(
            new BackupResponseDto("systempro-backup-20261014T050000Z.spbackup", 100L, Instant.parse("2026-10-14T05:00:00Z"))));

        scheduler(true, backupService, retention, dueService).checkScheduledBackup();

        verify(backupService).generateScheduled();
        verify(retention).applyScheduledRetention();
    }

    @Test
    void naoDeveGerarQuandoBackupAutomaticoAindaEstiverRecente() {
        BackupService backupService = mock(BackupService.class);
        BackupRetentionService retention = mock(BackupRetentionService.class);
        BackupScheduleDueService dueService = mock(BackupScheduleDueService.class);
        when(dueService.isScheduledBackupDue()).thenReturn(false);

        scheduler(true, backupService, retention, dueService).checkScheduledBackup();

        verifyNoInteractions(backupService, retention);
    }

    @Test
    void naoDeveExecutarRetencaoQuandoBackupEstiverOcupadoOuFalhar() {
        BackupScheduleDueService dueService = mock(BackupScheduleDueService.class);
        when(dueService.isScheduledBackupDue()).thenReturn(true);

        BackupService busyService = mock(BackupService.class);
        BackupRetentionService busyRetention = mock(BackupRetentionService.class);
        when(busyService.generateScheduled()).thenReturn(Optional.empty());
        scheduler(true, busyService, busyRetention, dueService).checkScheduledBackup();
        verifyNoInteractions(busyRetention);

        BackupService failingService = mock(BackupService.class);
        BackupRetentionService failingRetention = mock(BackupRetentionService.class);
        when(failingService.generateScheduled()).thenThrow(new BusinessException("falha"));
        scheduler(true, failingService, failingRetention, dueService).checkScheduledBackup();
        verifyNoInteractions(failingRetention);
    }

    @Test
    void naoDeveExecutarQuandoSchedulerEstiverDesabilitado() {
        BackupService backupService = mock(BackupService.class);
        BackupRetentionService retention = mock(BackupRetentionService.class);
        BackupScheduleDueService dueService = mock(BackupScheduleDueService.class);

        scheduler(false, backupService, retention, dueService).checkScheduledBackup();

        verifyNoInteractions(dueService, backupService, retention);
    }

    private BackupScheduler scheduler(
        boolean enabled,
        BackupService backupService,
        BackupRetentionService retention,
        BackupScheduleDueService dueService
    ) {
        return new BackupScheduler(backupService, retention, dueService,
            new BackupSchedulingProperties(enabled, 24, 3_600_000, 60_000, 30));
    }
}