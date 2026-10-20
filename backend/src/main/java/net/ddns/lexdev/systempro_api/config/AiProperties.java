package net.ddns.lexdev.systempro_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "systempro.ai")
public record AiProperties(
    boolean enabled,
    String provider,
    int maxOutputTokens,
    int connectTimeoutSeconds,
    int requestTimeoutSeconds,
    OpenAi openai
) {

    public record OpenAi(
        String apiKey,
        String baseUrl,
        String model
    ) {}
}
