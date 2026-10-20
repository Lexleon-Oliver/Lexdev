package net.ddns.lexdev.systempro_api.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import net.ddns.lexdev.systempro_api.config.AiProperties;
import net.ddns.lexdev.systempro_api.config.GeminiProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class GeminiGateway implements AiGateway {

    private static final String SYSTEM_INSTRUCTION =
        "Você é um assistente de gestão empresarial do SystemPro. "
            + "Produza um insight curto em português do Brasil, com no máximo "
            + "4 frases. Use exclusivamente os dados fornecidos. "
            + "Priorize vendas, ticket médio, tendência e pendências fiscais. "
            + "Não invente informações. Não use Markdown. "
            + "Não forneça aconselhamento jurídico, contábil ou fiscal.";

    private final AiProperties aiProperties;
    private final GeminiProperties properties;
    private final JsonMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiGateway(
        AiProperties aiProperties,
        GeminiProperties properties,
        JsonMapper objectMapper
    ) {
        this.aiProperties = aiProperties;
        this.properties = properties;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(
                Duration.ofSeconds(
                    Math.max(1, aiProperties.connectTimeoutSeconds())
                )
            )
            .build();
    }

    @Override
    public String generateInsight(String prompt) {
        validateConfiguration();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(interactionsUri())
            .timeout(
                Duration.ofSeconds(
                    Math.max(1, aiProperties.requestTimeoutSeconds())
                )
            )
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("x-goog-api-key", properties.apiKey().trim())
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    buildRequestBody(prompt),
                    StandardCharsets.UTF_8
                )
            )
            .build();

        try {
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
                "A chamada ao Gemini foi interrompida.",
                ex
            );

        } catch (IOException ex) {
            throw new IllegalStateException(
                "Falha de comunicação com o Gemini.",
                ex
            );
        }
    }

    String buildRequestBody(String prompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode();

            body.put("model", properties.model().trim());
            body.put("input", prompt);
            body.put("system_instruction", SYSTEM_INSTRUCTION);

            ObjectNode generationConfig =
                body.putObject("generation_config");

            generationConfig.put(
                "max_output_tokens",
                aiProperties.maxOutputTokens()
            );

            return objectMapper.writeValueAsString(body);

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                "Não foi possível montar a requisição para o Gemini.",
                ex
            );
        }
    }

    String extractOutputText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root == null) {
                throw new IllegalStateException(
                    "Gemini retornou uma resposta vazia."
                );
            }

            /*
             * Compatibilidade defensiva caso a API passe a expor
             * output_text diretamente no REST.
             */
            String directOutput = root.path("output_text").asText("");

            if (!directOutput.isBlank()) {
                return directOutput.trim();
            }

            StringBuilder output = new StringBuilder();

            JsonNode steps = root.path("steps");

            if (steps.isArray()) {
                for (JsonNode step : steps) {
                    if (!"model_output".equals(
                        step.path("type").asText("")
                    )) {
                        continue;
                    }

                    JsonNode content = step.path("content");

                    if (!content.isArray()) {
                        continue;
                    }

                    for (JsonNode item : content) {
                        if (!"text".equals(
                            item.path("type").asText("")
                        )) {
                            continue;
                        }

                        String text = item.path("text").asText("");

                        if (text.isBlank()) {
                            continue;
                        }

                        if (!output.isEmpty()) {
                            output.append('\n');
                        }

                        output.append(text.trim());
                    }
                }
            }

            if (output.isEmpty()) {
                throw new IllegalStateException(
                    "Gemini não retornou conteúdo textual."
                );
            }

            return output.toString();

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                "Gemini retornou JSON inválido.",
                ex
            );
        }
    }

    String buildProviderErrorMessage(
        int statusCode,
        String responseBody
    ) {
        String status = "unknown";
        String code = "unknown";

        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode error = root.path("error");

                status = diagnosticValue(
                    error.path("status").asText("")
                );

                code = diagnosticValue(
                    error.path("code").asText("")
                );

            } catch (JacksonException ignored) {
                // O HTTP status continua suficiente para o diagnóstico.
            }
        }

        return "Gemini respondeu com HTTP "
            + statusCode
            + ", status="
            + status
            + ", code="
            + code;
    }

    private URI interactionsUri() {
        String baseUrl = properties.baseUrl().trim();

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return URI.create(baseUrl + "/interactions");
    }

    private void validateConfiguration() {
        if (properties == null) {
            throw new IllegalStateException(
                "Configuração do Gemini não disponível."
            );
        }

        if (isBlank(properties.apiKey())) {
            throw new IllegalStateException(
                "GEMINI_API_KEY não configurada."
            );
        }

        if (isBlank(properties.baseUrl())) {
            throw new IllegalStateException(
                "GEMINI_BASE_URL não configurada."
            );
        }

        if (isBlank(properties.model())) {
            throw new IllegalStateException(
                "GEMINI_MODEL não configurado."
            );
        }

        if (aiProperties.maxOutputTokens() <= 0) {
            throw new IllegalStateException(
                "AI_MAX_OUTPUT_TOKENS deve ser maior que zero."
            );
        }
    }

    private static String diagnosticValue(String value) {
        if (isBlank(value)) {
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
