package net.ddns.lexdev.systempro_api.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import net.ddns.lexdev.systempro_api.config.BackupProperties;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;
import net.ddns.lexdev.systempro_api.dto.backup.BackupTrigger;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deveGerarListarECarregarBackupLocal() throws Exception {
        Path images = Files.createDirectories(tempDir.resolve("images"));
        Path documents = Files.createDirectories(tempDir.resolve("documents"));
        Path backups = tempDir.resolve("backups");

        BackupProperties properties = properties(backups, images, documents, "local");
        PostgreSqlDumpExecutor dumpExecutor = mock(PostgreSqlDumpExecutor.class);
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(0), "dump");
            return null;
        }).when(dumpExecutor).dump(any(Path.class));

        BackupPackageService packageService = mock(BackupPackageService.class);
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(0), "package");
            return null;
        }).when(packageService).createPackage(any(), any(), any(), any(), any());

        BackupPackageVerifier packageVerifier = mock(BackupPackageVerifier.class);
        BackupService service = new BackupService(
            properties,
            dumpExecutor,
            packageService,
            packageVerifier,
            Clock.fixed(Instant.parse("2026-10-14T02:14:53Z"), ZoneOffset.UTC)
        );

        var generated = service.generate();
        verify(packageVerifier).verify(any(Path.class));
        assertThat(generated.fileName()).isEqualTo("systempro-backup-20261014T021453Z.spbackup");
        assertThat(service.list()).hasSize(1);

        Resource resource = service.load(generated.fileName());
        assertThat(resource.exists()).isTrue();
    }

    @Test
    void naoDevePublicarPacoteQuandoAutoverificacaoFalhar() throws Exception {
        Path images = Files.createDirectories(tempDir.resolve("images-failure"));
        Path documents = Files.createDirectories(tempDir.resolve("documents-failure"));
        Path backups = tempDir.resolve("backups-failure");

        BackupProperties properties = properties(backups, images, documents, "local");
        PostgreSqlDumpExecutor dumpExecutor = mock(PostgreSqlDumpExecutor.class);
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(0), "dump");
            return null;
        }).when(dumpExecutor).dump(any(Path.class));

        BackupPackageService packageService = mock(BackupPackageService.class);
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(0), "package-corrompido");
            return null;
        }).when(packageService).createPackage(any(), any(), any(), any(), any());

        BackupPackageVerifier packageVerifier = mock(BackupPackageVerifier.class);
        doThrow(new java.io.IOException("checksum inválido"))
            .when(packageVerifier).verify(any(Path.class));

        BackupService service = new BackupService(
            properties,
            dumpExecutor,
            packageService,
            packageVerifier,
            Clock.fixed(Instant.parse("2026-10-14T02:14:53Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(service::generate)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("checksum inválido");

        if (Files.exists(backups)) {
            try (var files = Files.list(backups)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith(".spbackup")))
                    .isEmpty();
            }
        }
    }

    @Test
    void deveRecusarProviderS3NaFaseUm() throws Exception {
        Path images = Files.createDirectories(tempDir.resolve("images"));
        Path documents = Files.createDirectories(tempDir.resolve("documents"));
        BackupService service = new BackupService(
            properties(tempDir.resolve("backups"), images, documents, "s3"),
            mock(PostgreSqlDumpExecutor.class),
            mock(BackupPackageService.class),
            mock(BackupPackageVerifier.class)
        );

        assertThatThrownBy(service::generate)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("provider=local");
    }

    @Test
    void deveRecusarNomeDeArquivoManipulado() throws Exception {
        Path images = Files.createDirectories(tempDir.resolve("images"));
        Path documents = Files.createDirectories(tempDir.resolve("documents"));
        BackupService service = new BackupService(
            properties(tempDir.resolve("backups"), images, documents, "local"),
            mock(PostgreSqlDumpExecutor.class),
            mock(BackupPackageService.class),
            mock(BackupPackageVerifier.class)
        );

        assertThatThrownBy(() -> service.load("../../etc/passwd"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("inválido");
    }

    @Test
    void deveIdentificarOrigemManualEAutomaticaNoManifesto() throws Exception {
        Path images = Files.createDirectories(tempDir.resolve("images-trigger"));
        Path documents = Files.createDirectories(tempDir.resolve("documents-trigger"));
        Path backups = tempDir.resolve("backups-trigger");
        PostgreSqlDumpExecutor dumpExecutor = mock(PostgreSqlDumpExecutor.class);
        doAnswer(invocation -> { Files.writeString(invocation.getArgument(0), "dump"); return null; })
            .when(dumpExecutor).dump(any(Path.class));
        BackupPackageService packageService = mock(BackupPackageService.class);
        doAnswer(invocation -> { Files.writeString(invocation.getArgument(0), "package"); return null; })
            .when(packageService).createPackage(any(), any(), any(), any(), any());

        BackupService service = new BackupService(properties(backups, images, documents, "local"), dumpExecutor,
            packageService, mock(BackupPackageVerifier.class),
            Clock.fixed(Instant.parse("2026-10-14T02:14:53Z"), ZoneOffset.UTC));

        service.generate();
        Files.delete(backups.resolve("systempro-backup-20261014T021453Z.spbackup"));
        service.generateScheduled();

        ArgumentCaptor<BackupManifest> captor = ArgumentCaptor.forClass(BackupManifest.class);
        verify(packageService, times(2)).createPackage(any(), any(), any(), any(), captor.capture());
        assertThat(captor.getAllValues()).extracting(BackupManifest::trigger)
            .containsExactly(BackupTrigger.MANUAL, BackupTrigger.SCHEDULED);
    }

    private BackupProperties properties(Path backups, Path images, Path documents, String provider) {
        return new BackupProperties(
            backups.toString(),
            images.toString(),
            documents.toString(),
            provider,
            "db",
            5432,
            "systempro",
            "systempro",
            "secret",
            600
        );
    }
}