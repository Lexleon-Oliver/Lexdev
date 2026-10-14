package net.ddns.lexdev.systempro_api.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.BackupSchedulingProperties;
import net.ddns.lexdev.systempro_api.service.backup.BackupRetentionService;
import net.ddns.lexdev.systempro_api.service.backup.BackupService;

@Component
public class BackupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;
    private final BackupRetentionService retentionService;
    private final BackupSchedulingProperties properties;

    public BackupScheduler(
        BackupService backupService,
        BackupRetentionService retentionService,
        BackupSchedulingProperties properties
    ) {
        this.backupService = backupService;
        this.retentionService = retentionService;
        this.properties = properties;
    }

    @Scheduled(
        cron = "${systempro.backup.scheduling.cron:0 0 2 * * *}",
        zone = "${systempro.backup.scheduling.zone:America/Sao_Paulo}"
    )
    public void runScheduledBackup() {
        if (!properties.enabled()) {
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