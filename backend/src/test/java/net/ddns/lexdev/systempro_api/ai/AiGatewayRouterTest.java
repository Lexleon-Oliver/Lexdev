package net.ddns.lexdev.systempro_api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.config.AiProperties;

class AiGatewayRouterTest {

    @Test
    void deveUsarOpenAiQuandoConfigurado() {
        AiProperties properties = mock(AiProperties.class);
        OpenAiGateway openAi = mock(OpenAiGateway.class);
        GeminiGateway gemini = mock(GeminiGateway.class);

        when(properties.provider()).thenReturn("openai");
        when(openAi.generateInsight("prompt"))
            .thenReturn("openai-ok");

        AiGatewayRouter router =
            new AiGatewayRouter(
                properties,
                openAi,
                gemini
            );

        assertThat(
            router.generateInsight("prompt")
        ).isEqualTo("openai-ok");

        verify(openAi).generateInsight("prompt");
        verifyNoInteractions(gemini);
    }

    @Test
    void deveUsarGeminiQuandoConfigurado() {
        AiProperties properties = mock(AiProperties.class);
        OpenAiGateway openAi = mock(OpenAiGateway.class);
        GeminiGateway gemini = mock(GeminiGateway.class);

        when(properties.provider()).thenReturn("gemini");
        when(gemini.generateInsight("prompt"))
            .thenReturn("gemini-ok");

        AiGatewayRouter router =
            new AiGatewayRouter(
                properties,
                openAi,
                gemini
            );

        assertThat(
            router.generateInsight("prompt")
        ).isEqualTo("gemini-ok");

        verify(gemini).generateInsight("prompt");
        verifyNoInteractions(openAi);
    }

    @Test
    void deveRejeitarProviderDesconhecido() {
        AiProperties properties = mock(AiProperties.class);
        OpenAiGateway openAi = mock(OpenAiGateway.class);
        GeminiGateway gemini = mock(GeminiGateway.class);

        when(properties.provider())
            .thenReturn("desconhecido");

        AiGatewayRouter router =
            new AiGatewayRouter(
                properties,
                openAi,
                gemini
            );

        assertThatThrownBy(
            () -> router.generateInsight("prompt")
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(
                "Provedor de IA não suportado"
            );

        verifyNoInteractions(openAi, gemini);
    }
}
