package net.ddns.lexdev.systempro_api.service.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.ddns.lexdev.systempro_api.dto.backup.BackupManifest;

@Service
public class BackupPackageMetadataReader {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public BackupManifest readManifest(Path packageFile) throws IOException {
        try (ZipFile zip = new ZipFile(packageFile.toFile())) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null || entry.isDirectory()) {
                throw new IOException("manifest.json ausente no pacote de backup.");
            }
            try (var input = zip.getInputStream(entry)) {
                return objectMapper.readValue(input, BackupManifest.class);
            }
        }
    }
}