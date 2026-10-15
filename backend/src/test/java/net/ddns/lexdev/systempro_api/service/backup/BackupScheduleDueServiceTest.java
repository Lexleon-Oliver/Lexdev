package net.ddns.lexdev.systempro_api.service.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.ddns.lexdev.systempro_api.config.BackupProperties;
import net.ddns.lexdev.systempro_api.config.BackupSchedulingProperties;
import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;
import net.ddns.lexdev.systempro_api.dto.backup.BackupTrigger;

class BackupScheduleDueServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deveConsiderarVencidoQuandoNaoExistirBackupAutomatico() throws Exception {
        Path legacy = Files.createFile(tempDir.resolve("legacy.spbackup"));
        BackupPackageVerifier verifier = mock(BackupPackageVerifier.class);
        BackupPackageMetadataReader reader = mock(BackupPackageMetadataReader.class);
        when(reader.readManifest(legacy)).thenReturn(manifest(null, Instant.parse("2026-10-14T10:00:00Z")));

        assertTrue(service(verifier, reader).isScheduledBackupDue());
        verify(verifier, never()).verify(legacy);
    }

    @Test
    void devePreservarIntervaloQuandoBackupAutomaticoForRecente() throws Exception {
        Path scheduled = Files.createFile(tempDir.resolve("scheduled.spbackup"));
        BackupPackageVerifier verifier = mock(BackupPackageVerifier.class);
        BackupPackageMetadataReader reader = mock(BackupPackageMetadataReader.class);
        when(reader.readManifest(scheduled)).thenReturn(
            manifest(BackupTrigger.SCHEDULED, Instant.parse("2026-10-14T18:00:00Z")));

        assertFalse(service(verifier, reader).isScheduledBackupDue());
        verify(verifier).verify(scheduled);
    }

    @Test
    void deveConsiderarVencidoQuandoBackupAutomaticoUltrapassarIdadeMaxima() throws Exception {
        Path scheduled = Files.createFile(tempDir.resolve("scheduled.spbackup"));
        BackupPackageVerifier verifier = mock(BackupPackageVerifier.class);
        BackupPackageMetadataReader reader = mock(BackupPackageMetadataReader.class);
        when(reader.readManifest(scheduled)).thenReturn(
            manifest(BackupTrigger.SCHEDULED, Instant.parse("2026-10-13T18:00:00Z")));

        assertTrue(service(verifier, reader).isScheduledBackupDue());
    }

    private BackupScheduleDueService service(BackupPackageVerifier verifier, BackupPackageMetadataReader reader) {
        BackupProperties backup = new BackupProperties(
            tempDir.toString(), tempDir.toString(), tempDir.toString(), "local",
            "db", 5432, "systempro", "user", "password", 600);
        BackupSchedulingProperties scheduling = new BackupSchedulingProperties(true, 24, 3_600_000, 60_000, 30);
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T20:00:00Z"), ZoneOffset.UTC);
        return new BackupScheduleDueService(backup, scheduling, verifier, reader, clock);
    }

    private BackupManifest manifest(BackupTrigger trigger, Instant completedAt) {
        return new BackupManifest(
            1,
            completedAt.minusSeconds(10),
            completedAt,
            "PostgreSQL",
            "systempro",
            "local",
            java.util.List.of("database"),
            trigger
        );
    }
}