package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.clamav")
public record ClamAvProperties(
    String host,
    int port,
    int timeoutSeconds
) {}
