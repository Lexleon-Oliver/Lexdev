package net.ddns.lexdev.systempro_api.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import net.ddns.lexdev.systempro_api.config.AiProperties;

@Component
public class OpenAiGateway implements AiGateway {

    private static final String DEVELOPER_INSTRUCTIONS =
        "Você é um analista operacional integrado ao ERP SystemPro. "
        + "Gere um insight curto em português do Brasil, com no máximo quatro frases. "
        + "Use exclusivamente os dados fornecidos. "
        + "Priorize fatos úteis sobre vendas, ticket médio, tendência recente e pendências fiscais. "
        + "Não invente causas, previsões, valores ou fatos ausentes. "
        + "Não use markdown e não dê orientação jurídica, contábil ou fiscal.";

    private final AiProperties properties;
    private final JsonMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiGateway(
        AiProperties properties,
        JsonMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(
                positive(properties.connectTimeoutSeconds(), 10)
            ))
            .build();
    }

    @Override
    public String generateInsight(String prompt) {
        AiProperties.OpenAi openAi = requireConfiguration();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(responsesUrl(openAi.baseUrl())))
                .timeout(Duration.ofSeconds(
                    positive(properties.requestTimeoutSeconds(), 30)
                ))
                .header("Authorization", "Bearer " + openAi.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    buildRequestBody(prompt),
                    StandardCharsets.UTF_8
                ))
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                    buildProviderErrorMessage(
                        response.statusCode(),
                        response.body()
                    )
                );
            }

            return extractOutputText(response.body());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "A chamada à OpenAI foi interrompida.",
                ex
            );
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                "Falha de comunicação com a OpenAI.",
                ex
            );
        }
    }

    String buildRequestBody(String prompt) {
        AiProperties.OpenAi openAi = requireConfiguration();

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", openAi.model().trim());
        root.put("store", false);
        root.put(
            "max_output_tokens",
            positive(properties.maxOutputTokens(), 600)
        );

        root.putObject("reasoning")
            .put("effort", "none");

        ArrayNode input = root.putArray("input");

        input.addObject()
            .put("role", "developer")
            .put("content", DEVELOPER_INSTRUCTIONS);

        input.addObject()
            .put("role", "user")
            .put("content", prompt);

        return root.toString();
    }

    String buildProviderErrorMessage(
        int statusCode,
        String responseBody
    ) {
        String type = "unknown";
        String code = "unknown";

        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);

                if (root != null) {
                    JsonNode error = root.path("error");

                    type = diagnosticValue(
                        error.path("type").asText("")
                    );
                    code = diagnosticValue(
                        error.path("code").asText("")
                    );
                }
            } catch (JacksonException ignored) {
                // O status HTTP continua sendo suficiente para o fallback.
            }
        }

        return "OpenAI respondeu com HTTP "
            + statusCode
            + ", type="
            + type
            + ", code="
            + code;
    }

    private static String diagnosticValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        String sanitized = value
            .trim()
            .replaceAll("[^A-Za-z0-9_.-]", "_");

        if (sanitized.length() > 80) {
            return sanitized.substring(0, 80);
        }

        return sanitized;
    }

    String extractOutputText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root == null) {
                throw new IllegalStateException(
                    "A OpenAI retornou uma resposta vazia."
                );
            }

            for (JsonNode output : root.path("output")) {
                if (!"message".equals(output.path("type").asText())) {
                    continue;
                }

                for (JsonNode content : output.path("content")) {
                    if (!"output_text".equals(content.path("type").asText())) {
                        continue;
                    }

                    String text = content.path("text").asText("").trim();

                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }

            throw new IllegalStateException(
                "A OpenAI não retornou conteúdo textual."
            );

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                "A OpenAI retornou JSON inválido.",
                ex
            );
        }
    }

    private AiProperties.OpenAi requireConfiguration() {
        if (!"openai".equalsIgnoreCase(safe(properties.provider()))) {
            throw new IllegalStateException(
                "O provedor de IA configurado não é suportado."
            );
        }

        AiProperties.OpenAi openAi = properties.openai();

        if (openAi == null
            || isBlank(openAi.apiKey())
            || isBlank(openAi.baseUrl())
            || isBlank(openAi.model())) {

            throw new IllegalStateException(
                "A configuração da OpenAI está incompleta."
            );
        }

        return openAi;
    }

    private static String responsesUrl(String baseUrl) {
        String normalized = baseUrl.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(
                0,
                normalized.length() - 1
            );
        }

        return normalized + "/responses";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
