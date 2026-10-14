package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.backup.scheduling")
public record BackupSchedulingProperties(
    boolean enabled,
    String cron,
    String zone,
    int retentionScheduledDays
) {}