package net.ddns.lexdev.systempro_api.service.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;

class BackupPackageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deveEmpacotarBancoStoragesManifestoEChecksums() throws Exception {
        Path dump = Files.writeString(tempDir.resolve("systempro.dump"), "dump", StandardCharsets.UTF_8);
        Path images = Files.createDirectories(tempDir.resolve("images"));
        Files.writeString(images.resolve("produto.jpg"), "image", StandardCharsets.UTF_8);
        Path documents = Files.createDirectories(tempDir.resolve("documents"));
        Path documentFile = documents.resolve("sub/documento.pdf");
        Files.createDirectories(documentFile.getParent());
        Files.writeString(documentFile, "document", StandardCharsets.UTF_8);

        BackupPackageService service = new BackupPackageService(
            new BackupIntegrityService()
        );
        Path output = tempDir.resolve("backup.spbackup");
        BackupManifest manifest = new BackupManifest(
            1,
            Instant.parse("2026-10-14T02:00:00Z"),
            Instant.parse("2026-10-14T02:00:01Z"),
            "PostgreSQL",
            "systempro",
            "local",
            List.of("database", "product-images", "supplier-documents")
        );

        service.createPackage(output, dump, images, documents, manifest);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertThat(zip.getEntry("manifest.json")).isNotNull();
            assertThat(zip.getEntry("checksums.sha256")).isNotNull();
            assertThat(zip.getEntry("database/systempro.dump")).isNotNull();
            assertThat(zip.getEntry("storage/product-images/produto.jpg")).isNotNull();
            assertThat(zip.getEntry("storage/supplier-documents/sub/documento.pdf")).isNotNull();

            String manifestJson = new String(
                zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),
                StandardCharsets.UTF_8
            );
            assertThat(manifestJson).contains("\"startedAt\" : \"2026-10-14T02:00:00Z\"");
            assertThat(manifestJson).contains("\"databaseDumpCompletedAt\" : \"2026-10-14T02:00:01Z\"");
            assertThat(manifestJson).doesNotContain("\"completedAt\"");

            String checksums = new String(
                zip.getInputStream(zip.getEntry("checksums.sha256")).readAllBytes(),
                StandardCharsets.UTF_8
            );
            assertThat(checksums).contains("database/systempro.dump");
            assertThat(checksums).contains("storage/product-images/produto.jpg");
            assertThat(checksums).contains("storage/supplier-documents/sub/documento.pdf");
        }
    }
}