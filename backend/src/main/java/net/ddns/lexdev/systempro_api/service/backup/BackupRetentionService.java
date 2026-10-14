package net.ddns.lexdev.systempro_api.service.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.config.BackupProperties;
import net.ddns.lexdev.systempro_api.config.BackupSchedulingProperties;
import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;
import net.ddns.lexdev.systempro_api.dto.backup.BackupTrigger;

@Service
public class BackupRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(BackupRetentionService.class);

    private final BackupProperties backupProperties;
    private final BackupSchedulingProperties schedulingProperties;
    private final BackupPackageVerifier packageVerifier;
    private final BackupPackageMetadataReader metadataReader;
    private final Clock clock;

    @Autowired
    public BackupRetentionService(
        BackupProperties backupProperties,
        BackupSchedulingProperties schedulingProperties,
        BackupPackageVerifier packageVerifier,
        BackupPackageMetadataReader metadataReader
    ) {
        this(backupProperties, schedulingProperties, packageVerifier, metadataReader, Clock.systemUTC());
    }

    BackupRetentionService(
        BackupProperties backupProperties,
        BackupSchedulingProperties schedulingProperties,
        BackupPackageVerifier packageVerifier,
        BackupPackageMetadataReader metadataReader,
        Clock clock
    ) {
        this.backupProperties = backupProperties;
        this.schedulingProperties = schedulingProperties;
        this.packageVerifier = packageVerifier;
        this.metadataReader = metadataReader;
        this.clock = clock;
    }

    public int applyScheduledRetention() {
        int retentionDays = schedulingProperties.retentionScheduledDays();
        if (retentionDays <= 0) {
            logger.warn("Retenção automática ignorada: retention-scheduled-days deve ser maior que zero.");
            return 0;
        }

        Path directory = Path.of(backupProperties.storageLocation()).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            return 0;
        }

        Instant cutoff = clock.instant().minusSeconds(retentionDays * 86_400L);
        int deleted = 0;

        try (var stream = Files.list(directory)) {
            for (Path candidate : stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".spbackup"))
                .toList()) {

                try {
                    packageVerifier.verify(candidate);
                    BackupManifest manifest = metadataReader.readManifest(candidate);
                    if (manifest.trigger() != BackupTrigger.SCHEDULED
                        || manifest.startedAt() == null
                        || !manifest.startedAt().isBefore(cutoff)) {
                        continue;
                    }

                    Files.delete(candidate);
                    deleted++;
                    logger.info("Backup automático expirado removido pela retenção: {}", candidate.getFileName());
                } catch (IOException | RuntimeException ex) {
                    logger.warn("Backup preservado porque não pôde ser validado/removido pela retenção: {}", candidate.getFileName(), ex);
                }
            }
        } catch (IOException ex) {
            logger.error("Falha ao listar o repositório local durante a retenção de backups.", ex);
        }

        return deleted;
    }
}