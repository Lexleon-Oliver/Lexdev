package net.ddns.lexdev.systempro_api.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import net.ddns.lexdev.systempro_api.config.AiProperties;

class OpenAiGatewayTest {

    private JsonMapper objectMapper;
    private OpenAiGateway gateway;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();

        AiProperties properties = new AiProperties(
            true,
            "openai",
            600,
            10,
            30,
            new AiProperties.OpenAi(
                "sk-test",
                "https://api.openai.com/v1",
                "gpt-6-luna"
            )
        );

        gateway = new OpenAiGateway(
            properties,
            objectMapper
        );
    }

    @Test
    void deveMontarRequestDaResponsesApiSemArmazenamento() throws Exception {
        JsonNode request = objectMapper.readTree(
            gateway.buildRequestBody("Dados de teste")
        );

        assertThat(request.path("model").asText())
            .isEqualTo("gpt-6-luna");
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.path("max_output_tokens").asInt())
            .isEqualTo(600);
        assertThat(request.path("reasoning").path("effort").asText())
            .isEqualTo("none");
        assertThat(request.path("input").size()).isEqualTo(2);
    }

    @Test
    void deveExtrairOutputTextDaResposta() {
        String response = """
            {
              "id": "resp_test",
              "status": "completed",
              "output": [
                {
                  "type": "reasoning"
                },
                {
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "Insight operacional gerado."
                    }
                  ]
                }
              ]
            }
            """;

        assertThat(gateway.extractOutputText(response))
            .isEqualTo("Insight operacional gerado.");
    }

    @Test
    void deveExtrairCodigoSeguroDoErroDoProvedor() {
        String responseBody = """
            {
              "error": {
                "message": "You exceeded your current quota.",
                "type": "insufficient_quota",
                "code": "credit_balance_exhausted"
              }
            }
            """;

        String message = gateway.buildProviderErrorMessage(
            429,
            responseBody
        );

        assertThat(message)
            .isEqualTo(
                "OpenAI respondeu com HTTP 429, "
                    + "type=insufficient_quota, "
                    + "code=credit_balance_exhausted"
            );

        assertThat(message)
            .doesNotContain("You exceeded your current quota.");
    }

}
