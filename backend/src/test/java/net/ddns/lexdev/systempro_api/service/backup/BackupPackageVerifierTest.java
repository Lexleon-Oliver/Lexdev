package net.ddns.lexdev.systempro_api.service.backup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;

class BackupPackageVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void deveValidarPacoteIntegro() throws Exception {
        Path packageFile = createPackage();
        new BackupPackageVerifier(new BackupIntegrityService()).verify(packageFile);
    }

    @Test
    void deveRecusarPayloadAlteradoMesmoComZipEstruturalmenteValido() throws Exception {
        Path original = createPackage();
        Path corrupted = tempDir.resolve("corrupted.spbackup");

        try (ZipFile source = new ZipFile(original.toFile());
             ZipOutputStream target = new ZipOutputStream(
                 new BufferedOutputStream(Files.newOutputStream(corrupted)))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                target.putNextEntry(new ZipEntry(entry.getName()));
                if ("database/systempro.dump".equals(entry.getName())) {
                    target.write("dump-corrompido".getBytes(StandardCharsets.UTF_8));
                } else {
                    try (var input = source.getInputStream(entry)) {
                        input.transferTo(target);
                    }
                }
                target.closeEntry();
            }
        }

        BackupPackageVerifier verifier = new BackupPackageVerifier(new BackupIntegrityService());
        assertThatThrownBy(() -> verifier.verify(corrupted))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Checksum inválido");
    }

    private Path createPackage() throws Exception {
        Path dump = Files.writeString(tempDir.resolve("systempro.dump"), "dump", StandardCharsets.UTF_8);
        Path images = Files.createDirectories(tempDir.resolve("images"));
        Files.writeString(images.resolve("produto.jpg"), "image", StandardCharsets.UTF_8);
        Path documents = Files.createDirectories(tempDir.resolve("documents"));
        Files.writeString(documents.resolve("documento.pdf"), "document", StandardCharsets.UTF_8);
        Path packageFile = tempDir.resolve("backup-" + System.nanoTime() + ".spbackup");

        BackupManifest manifest = new BackupManifest(
            1,
            Instant.parse("2026-10-14T02:00:00Z"),
            Instant.parse("2026-10-14T02:00:01Z"),
            "PostgreSQL",
            "systempro",
            "local",
            List.of("database", "product-images", "supplier-documents")
        );
        new BackupPackageService(new BackupIntegrityService())
            .createPackage(packageFile, dump, images, documents, manifest);
        return packageFile;
    }
}