package net.ddns.lexdev.systempro_api.ai;

import java.util.Locale;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.AiProperties;

@Component
@Primary
public class AiGatewayRouter implements AiGateway {

    private final AiProperties properties;
    private final OpenAiGateway openAiGateway;
    private final GeminiGateway geminiGateway;

    public AiGatewayRouter(
        AiProperties properties,
        OpenAiGateway openAiGateway,
        GeminiGateway geminiGateway
    ) {
        this.properties = properties;
        this.openAiGateway = openAiGateway;
        this.geminiGateway = geminiGateway;
    }

    @Override
    public String generateInsight(String prompt) {
        String provider = normalize(properties.provider());

        return switch (provider) {
            case "openai" ->
                openAiGateway.generateInsight(prompt);

            case "gemini" ->
                geminiGateway.generateInsight(prompt);

            default ->
                throw new IllegalStateException(
                    "Provedor de IA não suportado: "
                        + (provider.isBlank() ? "<vazio>" : provider)
                );
        };
    }

    private static String normalize(String provider) {
        if (provider == null) {
            return "";
        }

        return provider
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
