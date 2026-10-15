package net.ddns.lexdev.systempro_api.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.ddns.lexdev.systempro_api.config.BackupProperties;
import net.ddns.lexdev.systempro_api.config.BackupSchedulingProperties;
import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;
import net.ddns.lexdev.systempro_api.dto.backup.BackupTrigger;

class BackupRetentionServiceTest {

    @TempDir Path tempDir;

    @Test
    void deveRemoverSomenteBackupAutomaticoExpirado() throws Exception {
        Path expired = Files.writeString(tempDir.resolve("systempro-backup-20260901T020000Z.spbackup"), "x");
        Path recent = Files.writeString(tempDir.resolve("systempro-backup-20261010T020000Z.spbackup"), "x");
        Path manual = Files.writeString(tempDir.resolve("systempro-backup-20260901T030000Z.spbackup"), "x");
        Path legacy = Files.writeString(tempDir.resolve("systempro-backup-20260901T040000Z.spbackup"), "x");

        BackupPackageMetadataReader reader = mock(BackupPackageMetadataReader.class);
        when(reader.readManifest(expired)).thenReturn(manifest("2026-09-01T02:00:00Z", BackupTrigger.SCHEDULED));
        when(reader.readManifest(recent)).thenReturn(manifest("2026-10-10T02:00:00Z", BackupTrigger.SCHEDULED));
        when(reader.readManifest(manual)).thenReturn(manifest("2026-09-01T03:00:00Z", BackupTrigger.MANUAL));
        when(reader.readManifest(legacy)).thenReturn(manifest("2026-09-01T04:00:00Z", null));

        BackupRetentionService service = new BackupRetentionService(
                    backupProperties(tempDir),
                    new BackupSchedulingProperties(
            true,
            24,
            3_600_000,
            60_000,
            30
        ),
            mock(BackupPackageVerifier.class),
            reader,
            Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.applyScheduledRetention()).isEqualTo(1);
        assertThat(expired).doesNotExist();
        assertThat(recent).exists();
        assertThat(manual).exists();
        assertThat(legacy).exists();
    }

    @Test
    void devePreservarPacoteQueNaoPodeSerValidado() throws Exception {
        Path candidate = Files.writeString(tempDir.resolve("systempro-backup-20260901T020000Z.spbackup"), "x");
        BackupPackageVerifier verifier = mock(BackupPackageVerifier.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("inválido")).when(verifier).verify(candidate);

        BackupRetentionService service = new BackupRetentionService(
                    backupProperties(tempDir),
                    new BackupSchedulingProperties(
            true,
            24,
            3_600_000,
            60_000,
            30
        ),
            verifier,
            mock(BackupPackageMetadataReader.class),
            Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(service.applyScheduledRetention()).isZero();
        assertThat(candidate).exists();
    }

    private BackupManifest manifest(String startedAt, BackupTrigger trigger) {
        Instant started = Instant.parse(startedAt);
        return new BackupManifest(1, started, started.plusSeconds(1), "PostgreSQL", "systempro",
            "local", List.of("database", "product-images", "supplier-documents"), trigger);
    }

    private BackupProperties backupProperties(Path backups) {
        return new BackupProperties(backups.toString(), "/images", "/documents", "local", "db", 5432,
            "systempro", "systempro", "secret", 600);
    }
}