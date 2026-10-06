package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.fiscal")
public record FiscalProperties(
    boolean enabled,
    int connectTimeoutSeconds,
    int requestTimeoutSeconds,
    String encryptionKey,
    String schemaVersion,
    String productVersion,
    String trustStorePath,
    String trustStorePassword,
    String certificateContentType,
    boolean productionReady
) {}
