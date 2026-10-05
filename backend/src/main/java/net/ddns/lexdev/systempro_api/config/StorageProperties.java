package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.storage")
public record StorageProperties(
    String endpoint,
    String region,
    String accessKey,
    String secretKey,
    String bucket,
    boolean pathStyleAccess,
    long maxFileSizeBytes
) {}
