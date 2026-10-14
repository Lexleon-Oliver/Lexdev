package net.ddns.lexdev.systempro_api.service.backup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;

@Service
public class BackupPackageService {

    private final ObjectMapper objectMapper;
    private final BackupIntegrityService integrityService;

    public BackupPackageService(BackupIntegrityService integrityService) {
        this.objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.integrityService = integrityService;
    }

    public void createPackage(
        Path packageFile,
        Path databaseDump,
        Path productImages,
        Path documents,
        BackupManifest manifest
    ) throws IOException {
        List<SourceEntry> sources = new ArrayList<>();
        sources.add(new SourceEntry(databaseDump, "database/systempro.dump"));
        collectFiles(productImages, "storage/product-images", sources);
        collectFiles(documents, "storage/supplier-documents", sources);
        sources.sort(Comparator.comparing(SourceEntry::archivePath));

        StringBuilder checksums = new StringBuilder();
        for (SourceEntry source : sources) {
            checksums.append(integrityService.sha256(source.path()))
                .append("  ")
                .append(source.archivePath())
                .append('\n');
        }

        try (ZipOutputStream zip = new ZipOutputStream(
            new BufferedOutputStream(Files.newOutputStream(packageFile)))) {

            writeBytes(zip, "manifest.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            writeBytes(zip, "checksums.sha256", checksums.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] buffer = new byte[64 * 1024];
            for (SourceEntry source : sources) {
                zip.putNextEntry(new ZipEntry(source.archivePath()));
                try (var input = Files.newInputStream(source.path())) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            zip.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void collectFiles(Path root, String archiveRoot, List<SourceEntry> target) throws IOException {
        if (!Files.exists(root)) {
            throw new IOException("Diretório obrigatório de backup não existe: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new IOException("Caminho obrigatório de backup não é um diretório: " + root);
        }

        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .forEach(path -> target.add(new SourceEntry(
                    path,
                    archiveRoot + "/" + root.relativize(path).toString().replace('\\', '/')
                )));
        }
    }

    private void writeBytes(ZipOutputStream zip, String path, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }

    private record SourceEntry(Path path, String archivePath) {}
}