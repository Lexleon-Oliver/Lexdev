package net.ddns.lexdev.systempro_api.service.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.stereotype.Service;

@Service
public class BackupPackageVerifier {

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String CHECKSUMS_ENTRY = "checksums.sha256";
    private static final String DATABASE_ENTRY = "database/systempro.dump";

    private final BackupIntegrityService integrityService;

    public BackupPackageVerifier(BackupIntegrityService integrityService) {
        this.integrityService = integrityService;
    }

    public void verify(Path packageFile) throws IOException {
        try (ZipFile zip = new ZipFile(packageFile.toFile())) {
            requireRegularEntry(zip, MANIFEST_ENTRY);
            ZipEntry checksumsEntry = requireRegularEntry(zip, CHECKSUMS_ENTRY);
            requireRegularEntry(zip, DATABASE_ENTRY);

            Map<String, String> expectedChecksums = parseChecksums(zip, checksumsEntry);
            if (!expectedChecksums.containsKey(DATABASE_ENTRY)) {
                throw new IOException("checksums.sha256 não referencia o dump obrigatório do PostgreSQL.");
            }

            Set<String> payloadEntries = new HashSet<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                validateEntryName(entry.getName());
                if (!entry.isDirectory() && !isMetadata(entry.getName())) {
                    payloadEntries.add(entry.getName());
                }
            }

            if (!payloadEntries.equals(expectedChecksums.keySet())) {
                throw new IOException("Conteúdo do pacote não corresponde integralmente ao checksums.sha256.");
            }

            for (Map.Entry<String, String> expected : expectedChecksums.entrySet()) {
                ZipEntry payload = requireRegularEntry(zip, expected.getKey());
                String actual;
                try (var input = zip.getInputStream(payload)) {
                    actual = integrityService.sha256(input);
                }
                if (!actual.equalsIgnoreCase(expected.getValue())) {
                    throw new IOException("Checksum inválido no pacote para: " + expected.getKey());
                }
            }
        }
    }

    private Map<String, String> parseChecksums(ZipFile zip, ZipEntry checksumsEntry) throws IOException {
        String content;
        try (var input = zip.getInputStream(checksumsEntry)) {
            content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, String> checksums = new HashMap<>();
        for (String line : content.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf("  ");
            if (separator != 64 || line.length() <= separator + 2) {
                throw new IOException("Linha inválida em checksums.sha256.");
            }
            String checksum = line.substring(0, separator);
            if (!checksum.matches("[0-9a-fA-F]{64}")) {
                throw new IOException("SHA-256 inválido em checksums.sha256.");
            }
            String entryName = line.substring(separator + 2);
            validateEntryName(entryName);
            if (isMetadata(entryName) || checksums.putIfAbsent(entryName, checksum) != null) {
                throw new IOException("Entrada inválida ou duplicada em checksums.sha256: " + entryName);
            }
        }

        if (checksums.isEmpty()) {
            throw new IOException("checksums.sha256 não contém payloads.");
        }
        return checksums;
    }

    private ZipEntry requireRegularEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("Entrada obrigatória ausente no pacote: " + name);
        }
        return entry;
    }

    private void validateEntryName(String name) throws IOException {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
            || name.contains("\\") || name.equals("..") || name.startsWith("../") || name.contains("/../")) {
            throw new IOException("Caminho inválido dentro do pacote de backup.");
        }
    }

    private boolean isMetadata(String name) {
        return MANIFEST_ENTRY.equals(name) || CHECKSUMS_ENTRY.equals(name);
    }
}