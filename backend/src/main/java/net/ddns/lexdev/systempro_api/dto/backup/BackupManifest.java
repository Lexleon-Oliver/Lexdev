package net.ddns.lexdev.systempro_api.dto.backup;

import java.time.Instant;
import java.util.List;

public record BackupManifest(
    int formatVersion,
    Instant startedAt,
    Instant databaseDumpCompletedAt,
    String databaseEngine,
    String databaseName,
    String storageProvider,
    List<String> includedComponents,
    BackupTrigger trigger
) {
    public BackupManifest(
        int formatVersion,
        Instant startedAt,
        Instant databaseDumpCompletedAt,
        String databaseEngine,
        String databaseName,
        String storageProvider,
        List<String> includedComponents
    ) {
        this(formatVersion, startedAt, databaseDumpCompletedAt, databaseEngine, databaseName,
            storageProvider, includedComponents, null);
    }
}