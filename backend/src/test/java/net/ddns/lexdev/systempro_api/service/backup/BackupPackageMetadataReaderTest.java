package net.ddns.lexdev.systempro_api.service.backup;

import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class BackupPackageMetadataReaderTest {

    @TempDir
    Path tempDir;

    private final BackupPackageMetadataReader reader =
            new BackupPackageMetadataReader();

    @Test
    void shouldReadLegacyManifestWithUnknownCompletedAtAndMissingTrigger()
            throws IOException {

        Path packageFile = tempDir.resolve("legacy.spbackup");

        String manifest = """
                {
                  "formatVersion": 1,
                  "startedAt": "2026-10-14T11:49:45Z",
                  "completedAt": "2026-10-14T11:49:46Z",
                  "databaseEngine": "PostgreSQL",
                  "databaseName": "lexdevdb",
                  "storageProvider": "local",
                  "includedComponents": [
                    "database",
                    "product-images",
                    "supplier-documents"
                  ]
                }
                """;

        try (ZipOutputStream zip =
                     new ZipOutputStream(Files.newOutputStream(packageFile))) {

            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        BackupManifest result = reader.readManifest(packageFile);

        assertThat(result.formatVersion()).isEqualTo(1);
        assertThat(result.databaseName()).isEqualTo("lexdevdb");
        assertThat(result.trigger()).isNull();
    }
}