package net.ddns.lexdev.systempro_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.json.JsonMapper;

import net.ddns.lexdev.systempro_api.ai.AiGateway;
import net.ddns.lexdev.systempro_api.config.AiProperties;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto.Status;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardResponseDto;

class DashboardInsightServiceTest {

    private DashboardService dashboardService;
    private AiGateway aiGateway;
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        aiGateway = mock(AiGateway.class);
        objectMapper = JsonMapper.builder().build();
    }

    @Test
    void deveRetornarDisabledSemChamarGatewayQuandoIaEstiverDesativada() {
        DashboardResponseDto dashboard = dashboard();
        when(dashboardService.dashboard()).thenReturn(dashboard);

        DashboardInsightService service = new DashboardInsightService(
            dashboardService,
            aiGateway,
            properties(false, "")
            ,
            objectMapper
        );

        var response = service.insight();

        assertThat(response.status()).isEqualTo(Status.DISABLED);
        assertThat(response.insight()).contains("desativados");
        verifyNoInteractions(aiGateway);
    }

    @Test
    void deveRetornarInsightGeradoQuandoGatewayResponder() {
        DashboardResponseDto dashboard = dashboard();
        when(dashboardService.dashboard()).thenReturn(dashboard);
        when(aiGateway.generateInsight(anyString()))
            .thenReturn("As vendas apresentam comportamento estável.");

        DashboardInsightService service = new DashboardInsightService(
            dashboardService,
            aiGateway,
            properties(true, "sk-test"),
            objectMapper
        );

        var response = service.insight();

        assertThat(response.status()).isEqualTo(Status.GENERATED);
        assertThat(response.insight())
            .isEqualTo("As vendas apresentam comportamento estável.");

        ArgumentCaptor<String> prompt =
            ArgumentCaptor.forClass(String.class);

        org.mockito.Mockito.verify(aiGateway)
            .generateInsight(prompt.capture());

        assertThat(prompt.getValue())
            .contains("2026-10-19")
            .contains("português do Brasil")
            .contains("dd/MM/yyyy")
            .contains("R$ 18,00")
            .contains("pelo menos três dias com movimentação")
            .contains("Não atribua a data do dashboard")
            .contains("data de referência da consulta")
            .contains("Não transforme ausência ou baixo volume de vendas")
            .contains("Não invente causas")
            .contains("Não conte a mesma pendência fiscal duas vezes")
            .contains("merecem acompanhamento")
            .contains("Não forneça aconselhamento jurídico, contábil ou fiscal")
            .doesNotContain("recentSales");
    }

    @Test
    void deveRetornarFallbackQuandoGatewayFalhar() {
        when(dashboardService.dashboard()).thenReturn(dashboard());
        when(aiGateway.generateInsight(anyString()))
            .thenThrow(new IllegalStateException("falha simulada"));

        DashboardInsightService service = new DashboardInsightService(
            dashboardService,
            aiGateway,
            properties(true, "sk-test"),
            objectMapper
        );

        var response = service.insight();

        assertThat(response.status()).isEqualTo(Status.UNAVAILABLE);
        assertThat(response.insight()).contains("Não foi possível");
    }

    private static DashboardResponseDto dashboard() {
        return new DashboardResponseDto(
            LocalDate.of(2026, 10, 19),
            null,
            null,
            List.of(),
            List.of(),
            List.of()
        );
    }

    private static AiProperties properties(
        boolean enabled,
        String apiKey
    ) {
        return new AiProperties(
            enabled,
            "openai",
            600,
            10,
            30,
            new AiProperties.OpenAi(
                apiKey,
                "https://api.openai.com/v1",
                "gpt-6-luna"
            )
        );
    }
}
