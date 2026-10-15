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
public class BackupScheduleDueService {

    private static final Logger logger = LoggerFactory.getLogger(BackupScheduleDueService.class);

    private final BackupProperties backupProperties;
    private final BackupSchedulingProperties schedulingProperties;
    private final BackupPackageVerifier packageVerifier;
    private final BackupPackageMetadataReader metadataReader;
    private final Clock clock;

    @Autowired
    public BackupScheduleDueService(
        BackupProperties backupProperties,
        BackupSchedulingProperties schedulingProperties,
        BackupPackageVerifier packageVerifier,
        BackupPackageMetadataReader metadataReader
    ) {
        this(backupProperties, schedulingProperties, packageVerifier, metadataReader, Clock.systemUTC());
    }

    BackupScheduleDueService(
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

    public boolean isScheduledBackupDue() {
        long maxAgeHours = schedulingProperties.maxAgeHours();
        if (maxAgeHours <= 0) {
            logger.warn("Backup automático considerado vencido: max-age-hours deve ser maior que zero.");
            return true;
        }

        Instant newestScheduledBackup = findNewestValidScheduledBackup();
        if (newestScheduledBackup == null) {
            return true;
        }

        Instant cutoff = clock.instant().minusSeconds(maxAgeHours * 3_600L);
        return newestScheduledBackup.isBefore(cutoff);
    }

    private Instant findNewestValidScheduledBackup() {
        Path directory = Path.of(backupProperties.storageLocation()).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            return null;
        }

        Instant newest = null;
        try (var stream = Files.list(directory)) {
            for (Path candidate : stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".spbackup"))
                .toList()) {

                try {
                    BackupManifest manifest = metadataReader.readManifest(candidate);
                    if (manifest.trigger() != BackupTrigger.SCHEDULED) {
                        continue;
                    }

                    packageVerifier.verify(candidate);
                    Instant timestamp = manifest.databaseDumpCompletedAt() != null
                        ? manifest.databaseDumpCompletedAt()
                        : manifest.startedAt();
                    if (timestamp != null && (newest == null || timestamp.isAfter(newest))) {
                        newest = timestamp;
                    }
                } catch (IOException | RuntimeException ex) {
                    logger.warn("Backup ignorado ao avaliar periodicidade porque não pôde ser validado: {}",
                        candidate.getFileName(), ex);
                }
            }
        } catch (IOException ex) {
            logger.error("Falha ao listar o repositório local ao avaliar periodicidade do backup.", ex);
            return null;
        }

        return newest;
    }
}