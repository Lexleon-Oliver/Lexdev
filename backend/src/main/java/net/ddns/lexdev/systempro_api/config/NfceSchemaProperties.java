package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.fiscal.schema")
public record NfceSchemaProperties(
    String location
) {}
