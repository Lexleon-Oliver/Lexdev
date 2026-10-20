package net.ddns.lexdev.systempro_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import net.ddns.lexdev.systempro_api.ai.AiGateway;
import net.ddns.lexdev.systempro_api.config.AiProperties;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto.Status;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardResponseDto;

@Service
public class DashboardInsightService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(DashboardInsightService.class);

    private static final String DISABLED_MESSAGE =
        "Os insights por IA estão desativados. "
        + "Os indicadores do dashboard continuam disponíveis normalmente.";

    private static final String UNAVAILABLE_MESSAGE =
        "Não foi possível gerar o insight por IA agora. "
        + "Os indicadores do dashboard continuam disponíveis normalmente.";

    private final DashboardService dashboardService;
    private final AiGateway aiGateway;
    private final AiProperties properties;
    private final JsonMapper objectMapper;

    public DashboardInsightService(
        DashboardService dashboardService,
        AiGateway aiGateway,
        AiProperties properties,
        JsonMapper objectMapper
    ) {
        this.dashboardService = dashboardService;
        this.aiGateway = aiGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public DashboardInsightResponseDto insight() {
        DashboardResponseDto dashboard = dashboardService.dashboard();

        if (!properties.enabled()) {
            return new DashboardInsightResponseDto(
                Status.DISABLED,
                DISABLED_MESSAGE
            );
        }

        if (!isProviderSupported()) {
            return new DashboardInsightResponseDto(
                Status.UNAVAILABLE,
                UNAVAILABLE_MESSAGE
            );
        }

        try {
            String insight = aiGateway.generateInsight(
                buildPrompt(dashboard)
            );

            return new DashboardInsightResponseDto(
                Status.GENERATED,
                insight
            );

        } catch (RuntimeException ex) {
            LOGGER.warn(
                "Falha na geração de insight por IA; "
                    + "fallback local será retornado. provider={}, exception={}, reason={}",
                safe(properties.provider()),
                ex.getClass().getSimpleName(),
                safe(ex.getMessage())
            );

            return new DashboardInsightResponseDto(
                Status.UNAVAILABLE,
                UNAVAILABLE_MESSAGE
            );
        }
    }

    private String buildPrompt(DashboardResponseDto dashboard) {
        try {
            ObjectNode data = objectMapper.createObjectNode();

            if (dashboard.date() == null) {
                data.putNull("date");
            } else {
                data.put("date", dashboard.date().toString());
            }

            /*
             * Data minimization:
             * recentSales não é enviado ao provedor externo.
             */
            data.set(
                "sales",
                objectMapper.valueToTree(dashboard.sales())
            );
            data.set(
                "fiscal",
                objectMapper.valueToTree(dashboard.fiscal())
            );
            data.set(
                "payments",
                objectMapper.valueToTree(dashboard.payments())
            );
            data.set(
                "dailyPerformance",
                objectMapper.valueToTree(dashboard.dailyPerformance())
            );

            return "Analise os indicadores operacionais abaixo e destaque "
                + "o ponto mais relevante para o gestor neste momento.\n"
                + "Dados do dashboard:\n"
                + data;

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                "Não foi possível serializar os dados do dashboard para a IA.",
                ex
            );
        }
    }

    private boolean isProviderSupported() {
        String provider = properties.provider();

        return provider != null
            && (
                "openai".equalsIgnoreCase(provider)
                || "gemini".equalsIgnoreCase(provider)
            );
    }

    private boolean configurationAvailable() {
        if (!"openai".equalsIgnoreCase(safe(properties.provider()))) {
            return false;
        }

        AiProperties.OpenAi openAi = properties.openai();

        return openAi != null
            && !isBlank(openAi.apiKey())
            && !isBlank(openAi.baseUrl())
            && !isBlank(openAi.model());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
