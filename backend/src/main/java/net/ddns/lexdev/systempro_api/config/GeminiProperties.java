package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.ai.gemini")
public record GeminiProperties(
    String apiKey,
    String baseUrl,
    String model
) {}
