package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.backup")
public record BackupProperties(
    String storageLocation,
    String productImagesLocation,
    String documentsLocation,
    String storageProvider,
    String databaseHost,
    int databasePort,
    String databaseName,
    String databaseUsername,
    String databasePassword,
    long pgDumpTimeoutSeconds
) {}