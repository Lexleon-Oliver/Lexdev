package net.ddns.lexdev.systempro_api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.config.AiProperties;
import net.ddns.lexdev.systempro_api.config.GeminiProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GeminiGatewayTest {

    private JsonMapper objectMapper;
    private GeminiGateway gateway;

    @BeforeEach
    void setUp() {
        AiProperties aiProperties = mock(AiProperties.class);

        when(aiProperties.connectTimeoutSeconds())
            .thenReturn(10);

        when(aiProperties.requestTimeoutSeconds())
            .thenReturn(30);

        when(aiProperties.maxOutputTokens())
            .thenReturn(600);

        GeminiProperties geminiProperties =
            new GeminiProperties(
                "test-api-key",
                "https://generativelanguage.googleapis.com/v1",
                "gemini-3.5-flash-lite"
            );

        objectMapper = JsonMapper.builder().build();

        gateway = new GeminiGateway(
            aiProperties,
            geminiProperties,
            objectMapper
        );
    }

    @Test
    void deveMontarPayloadDaInteractionsApi() {
        String body = gateway.buildRequestBody(
            "Analise o dashboard."
        );

        JsonNode json = objectMapper.readTree(body);

        assertThat(json.path("model").asText())
            .isEqualTo("gemini-3.5-flash-lite");

        assertThat(json.path("input").asText())
            .isEqualTo("Analise o dashboard.");

        assertThat(
            json.path("system_instruction").asText()
        ).isNotBlank();

        assertThat(
            json.path("generation_config")
                .path("max_output_tokens")
                .asInt()
        ).isEqualTo(600);
    }

    @Test
    void deveExtrairTextoDaRespostaDaInteractionsApi() {
        String response = """
            {
              "id": "interaction-test",
              "status": "completed",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "As vendas permaneceram estáveis."
                    }
                  ]
                }
              ]
            }
            """;

        assertThat(
            gateway.extractOutputText(response)
        ).isEqualTo(
            "As vendas permaneceram estáveis."
        );
    }

    @Test
    void deveExtrairDiagnosticoSeguroDeErro() {
        String response = """
            {
              "error": {
                "code": 429,
                "message": "Sensitive provider message",
                "status": "RESOURCE_EXHAUSTED"
              }
            }
            """;

        String message = gateway.buildProviderErrorMessage(
            429,
            response
        );

        assertThat(message)
            .isEqualTo(
                "Gemini respondeu com HTTP 429, "
                    + "status=RESOURCE_EXHAUSTED, code=429"
            );

        assertThat(message)
            .doesNotContain("Sensitive provider message");
    }
}
