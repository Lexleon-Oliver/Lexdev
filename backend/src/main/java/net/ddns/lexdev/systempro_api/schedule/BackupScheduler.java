package net.ddns.lexdev.systempro_api.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.BackupSchedulingProperties;
import net.ddns.lexdev.systempro_api.service.backup.BackupRetentionService;
import net.ddns.lexdev.systempro_api.service.backup.BackupScheduleDueService;
import net.ddns.lexdev.systempro_api.service.backup.BackupService;

@Component
public class BackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final BackupRetentionService retentionService;
    private final BackupScheduleDueService dueService;
    private final BackupSchedulingProperties properties;

    public BackupScheduler(
        BackupService backupService,
        BackupRetentionService retentionService,
        BackupScheduleDueService dueService,
        BackupSchedulingProperties properties
    ) {
        this.backupService = backupService;
        this.retentionService = retentionService;
        this.dueService = dueService;
        this.properties = properties;
    }

    @Scheduled(
        fixedDelayString = "${systempro.backup.scheduling.check-interval-millis:3600000}",
        initialDelayString = "${systempro.backup.scheduling.initial-delay-millis:60000}"
    )
    public void checkScheduledBackup() {
        if (!properties.enabled() || !dueService.isScheduledBackupDue()) {
            return;
        }

        try {
            backupService.generateScheduled().ifPresent(backup -> {
                logger.info("Backup automático concluído: {} ({} bytes).", backup.fileName(), backup.sizeBytes());
                int deleted = retentionService.applyScheduledRetention();
                if (deleted > 0) {
                    logger.info("Retenção de backups automáticos concluída: {} arquivo(s) removido(s).", deleted);
                }
            });
        } catch (RuntimeException ex) {
            logger.error("Falha na geração automática do backup. A retenção não será executada.", ex);
        }
    }
}