package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.backup.scheduling")
public record BackupSchedulingProperties(
    boolean enabled,
    long maxAgeHours,
    long checkIntervalMillis,
    long initialDelayMillis,
    int retentionScheduledDays
) {}